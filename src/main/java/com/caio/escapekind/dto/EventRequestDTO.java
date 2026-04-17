package com.caio.escapekind.dto;

import java.util.UUID;

public record EventRequestDTO(
        UUID sessionId,
        String nodeId,
        String choiceMade,
        String eventType
) {
}