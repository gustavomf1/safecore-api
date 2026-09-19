package com.safecore.service;

import com.safecore.dto.request.SyncItemRequest;
import com.safecore.dto.response.SyncItemResult;
import com.safecore.entity.SyncIdempotencia;
import com.safecore.repository.SyncIdempotenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncItemProcessor {

    private final NaoConformidadeService ncService;
    private final DesvioService desvioService;
    private final SyncIdempotenciaRepository idempotenciaRepository;

    @Transactional
    public SyncItemResult processarItem(SyncItemRequest item) {
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
            log.warn("SyncItemProcessor: erro ao processar localId={} tipo={}: {}",
                    item.localId(), item.tipo(), e.getMessage());
            return new SyncItemResult(item.localId(), null, "ERRO", e.getMessage());
        }
    }
}
