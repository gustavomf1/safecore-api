package com.safecore.dto.request;

import jakarta.validation.Valid;

public record SyncItemRequest(
        String localId,
        String tipo,
        @Valid NaoConformidadeRequest nc,
        @Valid DesvioRequest desvio
) {}
