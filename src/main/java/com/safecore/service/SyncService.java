package com.safecore.service;

import com.safecore.dto.request.SyncBatchRequest;
import com.safecore.dto.request.SyncItemRequest;
import com.safecore.dto.response.SyncBatchResponse;
import com.safecore.dto.response.SyncItemResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncService {

    private final SyncItemProcessor itemProcessor;

    public SyncBatchResponse processar(SyncBatchRequest batch) {
        List<SyncItemResult> results = new ArrayList<>();
        for (SyncItemRequest item : batch.items()) {
            // O try/catch fica FORA da fronteira transacional de processarItem
            // de propósito: assim, quando create() falha, o rollback da
            // transação daquele item completa de forma limpa (a exceção já
            // saiu do método @Transactional) antes de virar um resultado
            // "ERRO" aqui — isolando a falha a este item, sem derrubar o
            // restante do batch com UnexpectedRollbackException.
            try {
                UUID serverId = itemProcessor.processarItem(item);
                results.add(new SyncItemResult(item.localId(), serverId, "CRIADO", null));
            } catch (Exception e) {
                log.warn("SyncService: erro ao processar localId={} tipo={}: {}",
                        item.localId(), item.tipo(), e.getMessage());
                results.add(new SyncItemResult(item.localId(), null, "ERRO", e.getMessage()));
            }
        }
        return new SyncBatchResponse(results);
    }
}
