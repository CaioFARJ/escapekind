package com.caio.escapekind.dto;

import java.util.Map;

/**
 * DTO com as estatísticas agregadas de todas as sessões de jogo.
 * Exposto pelo endpoint GET /api/admin/stats.
 */
public record AdminStatsDTO(
        long totalSessions,
        long completedSessions,
        Double averageScore,
        Map<String, Long> finalDistribution
) {}
