package com.caio.escapekind.dto;

import java.util.List;

/**
 * Pagina de historico de sessoes.
 * Envolve a lista de resumos com os metadados de paginacao necessarios
 * para a navegacao no painel de administracao.
 */
public record SessionHistoryDTO(
        List<SessionSummaryDTO> sessions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
