package com.safecore.service;

import com.safecore.dto.request.NaoConformidadeRequest;
import com.safecore.dto.request.SyncBatchRequest;
import com.safecore.dto.request.SyncItemRequest;
import com.safecore.dto.response.SyncBatchResponse;
import com.safecore.entity.SyncIdempotencia;
import com.safecore.repository.SyncIdempotenciaRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração REAL (contexto Spring + JPA + transações reais, sem Mockito
 * no meio da fronteira transacional) para o cenário do Finding "Critical" do
 * round 2: um item que falha no meio de um batch NÃO pode derrubar o batch
 * inteiro com UnexpectedRollbackException.
 *
 * Como rodar manualmente (requer Postgres descartável, ver task-2-report.md):
 *
 *   docker run --rm -d --name safecore-sync-test-pg \
 *     -e POSTGRES_PASSWORD=test -e POSTGRES_USER=test -e POSTGRES_DB=safecore_test \
 *     -p 55432:5432 postgres:16
 *
 *   mvn test -Dtest=SyncItemProcessorIntegrationTest
 *
 * (e remover/comentar o @Disabled abaixo enquanto roda localmente — as
 * credenciais do Postgres descartável já estão fixas via @TestPropertySource,
 * batendo com o comando docker acima)
 *
 * Desabilitado por padrão em `mvn clean test` pelo mesmo motivo de
 * SafeCoreApiApplicationTests: não há Testcontainers nem H2 configurados no
 * projeto, então não há banco disponível automaticamente em CI/máquinas sem
 * Postgres rodando. Ver task-2-report.md para o output real desta execução
 * (rodada manualmente contra o Postgres descartável acima) tanto ANTES quanto
 * DEPOIS da correção do round 2.
 */
@Disabled("Requer Postgres descartável rodando manualmente - ver Javadoc da classe e task-2-report.md")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:55432/safecore_test",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
@Import({SyncItemProcessor.class, SyncService.class, NaoConformidadeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // cada chamada a processarItem deve abrir SUA PRÓPRIA transação, não herdar a do teste
class SyncItemProcessorIntegrationTest {

    @Autowired
    SyncService syncService;

    @Autowired
    SyncIdempotenciaRepository idempotenciaRepository;

    @MockBean
    DesvioService desvioService; // não é chamado neste cenário (os 2 itens são NC)

    @MockBean
    S3StorageService s3StorageService; // colaborador inerte de NaoConformidadeService, não-transacional, irrelevante ao bug

    @MockBean
    SecurityHelper securityHelper; // idem — create() falha antes de tocar nele

    @Test
    void batchContinuaProcessandoOutrosItens_quandoUmItemFalhaDentroDaTransacao() {
        // item1: estabelecimento inexistente -> NaoConformidadeService.create() lança
        // ResourceNotFoundException como PRIMEIRA linha do método real (@Transactional),
        // sem precisar de nenhum dado de fixture.
        NaoConformidadeRequest reqFalha = new NaoConformidadeRequest(
                UUID.randomUUID(), "Titulo", UUID.randomUUID(), "Desc", 3, 2,
                null, null, false, List.of(), false, null, List.of(), List.of(),
                UUID.randomUUID());
        SyncItemRequest itemFalha = new SyncItemRequest("local-falha", "NC", reqFalha, null);

        // item2: localId já registrado em sync_idempotencia -> processarItem() bate
        // no atalho de idempotência (retorna o serverId existente, sem chamar create()),
        // dentro de SUA PRÓPRIA transação de item, separada da do item1.
        UUID serverIdExistente = UUID.randomUUID();
        idempotenciaRepository.save(SyncIdempotencia.builder()
                .localId("local-ok")
                .tipo("NC")
                .serverId(serverIdExistente)
                .build());

        SyncItemRequest itemOk = new SyncItemRequest("local-ok", "NC", reqFalha, null);

        SyncBatchRequest batch = new SyncBatchRequest(List.of(itemFalha, itemOk));

        // A asserção central: processar() NÃO pode lançar UnexpectedRollbackException
        // (nem nenhuma outra exceção) por causa do item1 ter falhado.
        SyncBatchResponse response = syncService.processar(batch);

        assertThat(response.results()).hasSize(2);

        assertThat(response.results().get(0).localId()).isEqualTo("local-falha");
        assertThat(response.results().get(0).status()).isEqualTo("ERRO");
        assertThat(response.results().get(0).erro()).contains("Estabelecimento não encontrado");

        assertThat(response.results().get(1).localId()).isEqualTo("local-ok");
        assertThat(response.results().get(1).status()).isEqualTo("CRIADO");
        assertThat(response.results().get(1).serverId()).isEqualTo(serverIdExistente);

        // idempotência não foi gravada para o item que falhou
        assertThat(idempotenciaRepository.findById("local-falha")).isEmpty();
    }
}
