package com.caio.escapekind.dto;

public record EventResponseDTO(
        Long eventId,
        Integer totalScore,
        String finalReached,
        String message
) {}
