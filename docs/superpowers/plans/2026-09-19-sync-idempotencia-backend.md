# Idempotência do sync de NC/Desvio (backend) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fazer `SyncService.processar()` não duplicar NC/Desvio quando o mesmo item de batch é reenviado (retry de rede, reenvio manual do app).

**Architecture:** Nova tabela `sync_idempotencia` (local_id → server_id) checada antes de criar; `processarItem` passa a ser `@Transactional` no nível do item (não do batch inteiro), pra check+create+insert acontecerem atomicamente e um item com erro não derrubar os outros.

**Tech Stack:** Spring Boot, JPA/Hibernate, Flyway (migrations), Maven, JUnit 5 + Mockito + AssertJ (padrão já usado em `SyncServiceTest.java`, sem Testcontainers/H2 — este projeto só valida migration contra Postgres real).

**Spec:** `/home/mag/Documents/mobile/safecore-mobile/docs/superpowers/specs/2026-09-19-sync-idempotencia-backend-design.md`

## Global Constraints

- `local_id` é `VARCHAR`, não `UUID` — `SyncItemRequest.localId()`/`SyncItemResult.localId()` já são `String` no contrato atual (confirmado lendo os records).
- Próxima migration é `V59` (última hoje: `V58__rename_seed_to_safecore.sql`).
- Nenhuma mudança nos DTOs de criação de NC/Desvio nem no fluxo de criação online do app mobile.
- Repositório é `/home/mag/Documents/Java Projects/EngSeg/safecore-api` (Maven; `mvn test -Dtest=<Classe>` para rodar um teste específico).

---

### Task 1: Migration + entidade + repositório `SyncIdempotencia`

**Files:**
- Create: `src/main/resources/db/migration/V59__criar_sync_idempotencia.sql`
- Create: `src/main/java/com/safecore/entity/SyncIdempotencia.java`
- Create: `src/main/java/com/safecore/repository/SyncIdempotenciaRepository.java`

**Interfaces:**
- Produces: `SyncIdempotenciaRepository extends JpaRepository<SyncIdempotencia, String>` com `findById(String localId): Optional<SyncIdempotencia>` (herdado) e `save(SyncIdempotencia): SyncIdempotencia` (herdado) — é isso que a Task 2 consome.
- Produces: `SyncIdempotencia` com getters `getLocalId(): String`, `getTipo(): String`, `getServerId(): UUID`, `getCriadoEm(): LocalDateTime`, e `SyncIdempotencia.builder()` (Lombok `@Builder`).

- [ ] **Step 1: Escrever a migration**

```sql
-- src/main/resources/db/migration/V59__criar_sync_idempotencia.sql
CREATE TABLE sync_idempotencia (
    local_id   VARCHAR(64) PRIMARY KEY,
    tipo       VARCHAR(10) NOT NULL,
    server_id  UUID NOT NULL,
    criado_em  TIMESTAMP NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Escrever a entidade, espelhando o padrão de `RefreshToken.java`**

```java
// src/main/java/com/safecore/entity/SyncIdempotencia.java
package com.safecore.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sync_idempotencia")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncIdempotencia {

    @Id
    @Column(name = "local_id", length = 64)
    private String localId;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "server_id", nullable = false)
    private UUID serverId;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
```

- [ ] **Step 3: Escrever o repositório**

```java
// src/main/java/com/safecore/repository/SyncIdempotenciaRepository.java
package com.safecore.repository;

import com.safecore.entity.SyncIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncIdempotenciaRepository extends JpaRepository<SyncIdempotencia, String> {
}
```

- [ ] **Step 4: Compilar**

Run: `cd "/home/mag/Documents/Java Projects/EngSeg/safecore-api" && mvn compile -q`
Expected: sem erros (exit code 0).

- [ ] **Step 5: Validar a migration contra Postgres real**

Este projeto não usa H2/Testcontainers (só `postgresql` como driver nos testes)
— migration é validada subindo o Postgres local e o app uma vez:

Run:
```bash
cd "/home/mag/Documents/Java Projects/EngSeg/safecore-api"
docker compose up -d postgres
# aguardar healthy: docker compose ps
DB_PASSWORD=<mesma senha do .env local> mvn spring-boot:run
```
Expected: no log, uma linha `Flyway ... Successfully applied 1 migration to
schema "public"` (ou similar) mencionando `V59`, sem erro de schema. Depois
de confirmar, interrompa o processo (Ctrl+C) — não precisa deixar rodando.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V59__criar_sync_idempotencia.sql \
        src/main/java/com/safecore/entity/SyncIdempotencia.java \
        src/main/java/com/safecore/repository/SyncIdempotenciaRepository.java
git commit -m "feat: adiciona tabela e entidade sync_idempotencia"
```

---

### Task 2: Idempotência em `SyncService.processarItem`

**Files:**
- Modify: `src/main/java/com/safecore/service/SyncService.java`
- Test: `src/test/java/com/safecore/service/SyncServiceTest.java`

**Interfaces:**
- Consumes: `SyncIdempotenciaRepository` (Task 1) — `findById(String): Optional<SyncIdempotencia>`, `save(SyncIdempotencia): SyncIdempotencia`.
- Consumes: `SyncIdempotencia.builder().localId(String).tipo(String).serverId(UUID).build()` (Task 1).

- [ ] **Step 1: Escrever o teste que falha — item repetido não duplica e devolve o `serverId` já existente**

Adicionar ao `SyncServiceTest.java` existente (mesmo padrão de mock dos dois
testes já presentes). O arquivo hoje importa `com.safecore.dto.request.*`,
`com.safecore.dto.response.*`, `java.util.List` e `java.util.UUID` — falta
adicionar:

```java
import com.safecore.entity.SyncIdempotencia;
import com.safecore.repository.SyncIdempotenciaRepository;
import java.util.Optional;
```

```java
@Mock SyncIdempotenciaRepository idempotenciaRepository;

@Test
void deveRetornarServerIdExistente_semChamarCreateDeNovo_quandoLocalIdJaProcessado() {
    NaoConformidadeRequest ncReq = new NaoConformidadeRequest(
            UUID.randomUUID(), "Titulo", UUID.randomUUID(), "Desc", 3, 2,
            null, null, false, List.of(), false, null, List.of(), List.of(), UUID.randomUUID());
    SyncItemRequest item = new SyncItemRequest("local-repetido", "NC", ncReq, null);
    SyncBatchRequest batch = new SyncBatchRequest(List.of(item));

    UUID serverIdExistente = UUID.randomUUID();
    when(idempotenciaRepository.findById("local-repetido"))
            .thenReturn(Optional.of(SyncIdempotencia.builder()
                    .localId("local-repetido").tipo("NC").serverId(serverIdExistente).build()));

    SyncBatchResponse result = syncService.processar(batch);

    assertThat(result.results().get(0).status()).isEqualTo("CRIADO");
    assertThat(result.results().get(0).serverId()).isEqualTo(serverIdExistente);
    verify(ncService, never()).create(any());
}

@Test
void deveGravarIdempotencia_apenasQuandoCriaComSucesso() {
    NaoConformidadeRequest ncReq = new NaoConformidadeRequest(
            UUID.randomUUID(), "Titulo", UUID.randomUUID(), "Desc", 3, 2,
            null, null, false, List.of(), false, null, List.of(), List.of(), UUID.randomUUID());
    SyncItemRequest item = new SyncItemRequest("local-novo", "NC", ncReq, null);
    SyncBatchRequest batch = new SyncBatchRequest(List.of(item));

    UUID novoServerId = UUID.randomUUID();
    NaoConformidadeResponse mockResponse = mock(NaoConformidadeResponse.class);
    when(mockResponse.id()).thenReturn(novoServerId);
    when(idempotenciaRepository.findById("local-novo")).thenReturn(Optional.empty());
    when(ncService.create(any())).thenReturn(mockResponse);

    syncService.processar(batch);

    verify(idempotenciaRepository).save(argThat(si ->
            si.getLocalId().equals("local-novo") && si.getServerId().equals(novoServerId)));
}
```

Ajustar o `@Mock`/`@InjectMocks` no topo da classe: adicionar
`@Mock SyncIdempotenciaRepository idempotenciaRepository;` junto dos mocks
já existentes (`ncService`, `desvioService`) — o Mockito injeta os três no
`syncService` via `@InjectMocks`.

- [ ] **Step 2: Rodar os testes novos e confirmar que falham**

Run: `cd "/home/mag/Documents/Java Projects/EngSeg/safecore-api" && mvn test -Dtest=SyncServiceTest -q`
Expected: FAIL — `SyncService` ainda não tem o construtor/campo
`idempotenciaRepository` nem a lógica de checagem (erro de compilação do
teste, ou `NullPointerException`/mock nunca chamado, dependendo de qual
step você rodar primeiro — o esperado aqui é qualquer falha que não seja
"passou").

- [ ] **Step 3: Implementar a checagem de idempotência, com `@Transactional` no nível do item**

```java
// src/main/java/com/safecore/service/SyncService.java
package com.safecore.service;

import com.safecore.dto.request.SyncBatchRequest;
import com.safecore.dto.request.SyncItemRequest;
import com.safecore.dto.response.SyncBatchResponse;
import com.safecore.dto.response.SyncItemResult;
import com.safecore.entity.SyncIdempotencia;
import com.safecore.repository.SyncIdempotenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final NaoConformidadeService ncService;
    private final DesvioService desvioService;
    private final SyncIdempotenciaRepository idempotenciaRepository;

    public SyncBatchResponse processar(SyncBatchRequest batch) {
        List<SyncItemResult> results = new ArrayList<>();
        for (SyncItemRequest item : batch.items()) {
            results.add(processarItem(item));
        }
        return new SyncBatchResponse(results);
    }

    @Transactional
    protected SyncItemResult processarItem(SyncItemRequest item) {
        try {
            var existente = idempotenciaRepository.findById(item.localId());
            if (existente.isPresent()) {
                return new SyncItemResult(item.localId(), existente.get().getServerId(), "CRIADO", null);
            }

            UUID serverId = switch (item.tipo()) {
                case "NC" -> ncService.create(item.nc()).id();
                case "DESVIO" -> desvioService.create(item.desvio()).id();
                default -> throw new IllegalArgumentException("tipo desconhecido: " + item.tipo());
            };

            idempotenciaRepository.save(SyncIdempotencia.builder()
                    .localId(item.localId())
                    .tipo(item.tipo())
                    .serverId(serverId)
                    .build());

            return new SyncItemResult(item.localId(), serverId, "CRIADO", null);
        } catch (Exception e) {
            log.warn("SyncService: erro ao processar localId={} tipo={}: {}",
                    item.localId(), item.tipo(), e.getMessage());
            return new SyncItemResult(item.localId(), null, "ERRO", e.getMessage());
        }
    }
}
```

Nota: `processarItem` precisa ser `protected` (ou `public`), nunca
`private`, para o proxy `@Transactional` do Spring funcionar — método
privado não é interceptado.

- [ ] **Step 4: Rodar os testes de novo, confirmar que passam — incluindo os dois já existentes**

Run: `cd "/home/mag/Documents/Java Projects/EngSeg/safecore-api" && mvn test -Dtest=SyncServiceTest -q`
Expected: PASS, os 4 testes (2 antigos + 2 novos).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/safecore/service/SyncService.java \
        src/test/java/com/safecore/service/SyncServiceTest.java
git commit -m "feat: idempotência por localId no sync de NC/Desvio"
```
