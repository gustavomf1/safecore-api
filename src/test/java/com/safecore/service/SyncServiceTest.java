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

        SyncItemResult result1 = new SyncItemResult("local-1", UUID.randomUUID(), "CRIADO", null);
        SyncItemResult result2 = new SyncItemResult("local-2", UUID.randomUUID(), "CRIADO", null);
        when(itemProcessor.processarItem(item1)).thenReturn(result1);
        when(itemProcessor.processarItem(item2)).thenReturn(result2);

        SyncBatchResponse response = syncService.processar(batch);

        assertThat(response.results()).containsExactly(result1, result2);
        verify(itemProcessor).processarItem(item1);
        verify(itemProcessor).processarItem(item2);
    }
}
