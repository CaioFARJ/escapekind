package com.caio.escapekind.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionFinishResponseDTO(
        UUID sessionId,
        Integer finalScore,
        String finalReached,
        LocalDateTime endedAt,
        String message
) {}
