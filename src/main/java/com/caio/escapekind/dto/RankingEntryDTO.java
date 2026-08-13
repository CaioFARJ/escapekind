package com.caio.escapekind.dto;

import java.time.LocalDateTime;

/**
 * Entrada individual do ranking de sessoes concluidas.
 * Exposta publicamente em GET /api/ranking.
 *
 * Nao inclui o UUID da sessao: o ranking e publico e o identificador
 * tecnico nao deve circular fora do painel de administracao.
 */
public record RankingEntryDTO(
        int position,
        String nickname,
        int safeSupportScore,
        String finalReached,
        Long durationSeconds,
        LocalDateTime startedAt
) {}
