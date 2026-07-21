package com.caio.escapekind.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EventRequestDTO(
        @NotNull UUID sessionId,
        @NotBlank String nodeId,
        @NotBlank String choiceMade,
        String eventType
) {}
