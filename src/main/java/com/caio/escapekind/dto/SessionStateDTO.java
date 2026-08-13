package com.caio.escapekind.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Estado atual de uma sessao de jogo, devolvido por GET /api/sessions/{id}.
 *
 * Permite ao front-end retomar uma partida interrompida: ao recarregar a
 * pagina, o cliente le o sessionId guardado em localStorage e valida-o contra
 * o servidor antes de continuar. Se a sessao ja nao existir, ou tiver sido
 * encerrada, o cliente descarta o estado local e inicia uma nova partida.
 *
 * O endpoint e publico porque o UUID funciona como segredo: so quem iniciou a
 * sessao o conhece. Nao expoe qualquer dado pessoal.
 */
public record SessionStateDTO(
        UUID sessionId,
        String nickname,
        Integer safeSupportScore,
        String finalReached,
        LocalDateTime startedAt
) {}
