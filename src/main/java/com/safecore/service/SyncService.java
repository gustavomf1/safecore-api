package com.safecore.service;

import com.safecore.dto.request.SyncBatchRequest;
import com.safecore.dto.request.SyncItemRequest;
import com.safecore.dto.response.SyncBatchResponse;
import com.safecore.dto.response.SyncItemResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyncService {

    private final SyncItemProcessor itemProcessor;

    public SyncBatchResponse processar(SyncBatchRequest batch) {
        List<SyncItemResult> results = new ArrayList<>();
        for (SyncItemRequest item : batch.items()) {
            results.add(itemProcessor.processarItem(item));
        }
        return new SyncBatchResponse(results);
    }
}
