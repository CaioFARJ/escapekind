package com.caio.escapekind.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resumo de uma sessao de jogo, usado no historico do painel de administracao.
 * Exposto em GET /api/admin/sessions (requer papel ADMIN).
 */
public record SessionSummaryDTO(
        UUID id,
        String nickname,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        Long durationSeconds,
        Integer safeSupportScore,
        String finalReached,
        long eventCount
) {}
