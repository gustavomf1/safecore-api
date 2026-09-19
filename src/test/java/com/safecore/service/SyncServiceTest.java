package com.safecore.service;

import com.safecore.dto.request.*;
import com.safecore.dto.response.*;
import com.safecore.entity.SyncIdempotencia;
import com.safecore.repository.SyncIdempotenciaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock NaoConformidadeService ncService;
    @Mock DesvioService desvioService;
    @Mock SyncIdempotenciaRepository idempotenciaRepository;
    @InjectMocks SyncService syncService;

    @Test
    void deveRetornarCRIADO_quandoNcProcessadaComSucesso() {
        NaoConformidadeRequest ncReq = new NaoConformidadeRequest(
                UUID.randomUUID(), "Titulo", null, "Desc", 3, 2,
                null, null, false, List.of(), false, null, List.of(), List.of(), null);
        SyncItemRequest item = new SyncItemRequest("local-1", "NC", ncReq, null);
        SyncBatchRequest batch = new SyncBatchRequest(List.of(item));

        NaoConformidadeResponse mockResponse = mock(NaoConformidadeResponse.class);
        when(mockResponse.id()).thenReturn(UUID.randomUUID());
        when(ncService.create(any())).thenReturn(mockResponse);

        SyncBatchResponse result = syncService.processar(batch);

        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).status()).isEqualTo("CRIADO");
        assertThat(result.results().get(0).localId()).isEqualTo("local-1");
        assertThat(result.results().get(0).serverId()).isNotNull();
    }

    @Test
    void deveRetornarERRO_quandoNcLancaExcecao() {
        NaoConformidadeRequest ncReq = new NaoConformidadeRequest(
                UUID.randomUUID(), "Titulo", null, "Desc", 3, 2,
                null, null, false, List.of(), false, null, List.of(), List.of(), null);
        SyncItemRequest item = new SyncItemRequest("local-2", "NC", ncReq, null);
        SyncBatchRequest batch = new SyncBatchRequest(List.of(item));

        when(ncService.create(any())).thenThrow(new RuntimeException("estabelecimento não encontrado"));

        SyncBatchResponse result = syncService.processar(batch);

        assertThat(result.results().get(0).status()).isEqualTo("ERRO");
        assertThat(result.results().get(0).erro()).contains("estabelecimento não encontrado");
    }

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
}
