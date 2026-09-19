package com.safecore.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SyncItemRequest(
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "localId deve ser um UUID válido"
        )
        String localId,
        @NotNull String tipo,
        @Valid NaoConformidadeRequest nc,
        @Valid DesvioRequest desvio
) {}
