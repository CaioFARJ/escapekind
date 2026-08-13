package com.caio.escapekind.dto;

import java.util.UUID;

/**
 * Resposta a criacao de uma sessao de jogo.
 *
 * O sessionId e o unico identificador do jogador: e gerado pelo servidor,
 * devolvido ao cliente e usado em todas as chamadas seguintes, sem que exista
 * qualquer conta ou credencial associada.
 *
 * O nickname devolvido e o valor efetivamente persistido apos higienizacao,
 * que pode diferir do enviado pelo jogador. O front-end apresenta este valor,
 * garantindo que o jogador ve exatamente o pseudonimo que aparecera no ranking.
 */
public record SessionResponseDTO(UUID sessionId, String nickname, String message) {}
