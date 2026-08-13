package com.caio.escapekind.dto;

import jakarta.validation.constraints.Size;

/**
 * Corpo opcional do pedido POST /api/sessions.
 *
 * O jogador pode indicar um pseudonimo para aparecer no ranking.
 * Se o corpo for omitido, ou o campo vier vazio, a sessao e criada
 * com o pseudonimo por omissao ("Anonimo"). Nao ha qualquer campo
 * obrigatorio: o jogo continua a poder ser jogado sem identificacao.
 */
public record SessionStartRequestDTO(
        @Size(max = 20, message = "O pseudonimo nao pode exceder 20 caracteres.")
        String nickname
) {}
