package com.safecore.service;

import com.safecore.dto.request.SyncBatchRequest;
import com.safecore.dto.request.SyncItemRequest;
import com.safecore.dto.response.SyncBatchResponse;
import com.safecore.dto.response.SyncItemResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock SyncItemProcessor itemProcessor;
    @InjectMocks SyncService syncService;

    @Test
    void deveDelegarProcessamentoDeCadaItemAoSyncItemProcessor() {
        SyncItemRequest item1 = new SyncItemRequest("local-1", "NC", null, null);
        SyncItemRequest item2 = new SyncItemRequest("local-2", "DESVIO", null, null);
        SyncBatchRequest batch = new SyncBatchRequest(List.of(item1, item2));

        UUID serverId1 = UUID.randomUUID();
        UUID serverId2 = UUID.randomUUID();
        when(itemProcessor.processarItem(item1)).thenReturn(serverId1);
        when(itemProcessor.processarItem(item2)).thenReturn(serverId2);

        SyncBatchResponse response = syncService.processar(batch);

        assertThat(response.results()).containsExactly(
                new SyncItemResult("local-1", serverId1, "CRIADO", null),
                new SyncItemResult("local-2", serverId2, "CRIADO", null));
        verify(itemProcessor).processarItem(item1);
        verify(itemProcessor).processarItem(item2);
    }

    @Test
    void deveIsolarErroDeUmItem_semInterromperOProcessamentoDosDemais_quandoItemProcessorLancaExcecao() {
        // Round 2 (Finding Critical): o try/catch mora aqui, FORA da fronteira
        // @Transactional de SyncItemProcessor.processarItem, exatamente para
        // que uma falha em um item não derrube o restante do batch. Este teste
        // cobre a semântica de resultado (ERRO isolado a um item, batch
        // continua); NÃO substitui a verificação de que o rollback da
        // transação de fato completa sem UnexpectedRollbackException — isso
        // só é observável com um PlatformTransactionManager real, fora do
        // alcance do Mockito (ver SyncItemProcessorIntegrationTest e a seção
        // de limitações no relatório).
        SyncItemRequest item1 = new SyncItemRequest("local-falha", "NC", null, null);
        SyncItemRequest item2 = new SyncItemRequest("local-ok", "DESVIO", null, null);
        SyncBatchRequest batch = new SyncBatchRequest(List.of(item1, item2));

        UUID serverId2 = UUID.randomUUID();
        when(itemProcessor.processarItem(item1))
                .thenThrow(new RuntimeException("estabelecimento não encontrado"));
        when(itemProcessor.processarItem(item2)).thenReturn(serverId2);

        SyncBatchResponse response = syncService.processar(batch);

        assertThat(response.results()).containsExactly(
                new SyncItemResult("local-falha", null, "ERRO", "estabelecimento não encontrado"),
                new SyncItemResult("local-ok", serverId2, "CRIADO", null));
        verify(itemProcessor).processarItem(item1);
        verify(itemProcessor).processarItem(item2);
    }
}
