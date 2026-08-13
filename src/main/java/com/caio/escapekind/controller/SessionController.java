package com.caio.escapekind.controller;

import com.caio.escapekind.dto.AdminStatsDTO;
import com.caio.escapekind.dto.RankingEntryDTO;
import com.caio.escapekind.dto.SessionFinishResponseDTO;
import com.caio.escapekind.dto.SessionHistoryDTO;
import com.caio.escapekind.dto.SessionResponseDTO;
import com.caio.escapekind.dto.SessionStartRequestDTO;
import com.caio.escapekind.dto.SessionStateDTO;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.service.GameSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
public class SessionController {

    private final GameSessionService sessionService;

    public SessionController(GameSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * POST /api/sessions
     * Inicia uma nova sessão de jogo anónima.
     *
     * O corpo do pedido é opcional. Quando presente, pode conter um pseudónimo:
     *   { "nickname": "Rita" }
     *
     * Se for omitido, vazio ou rejeitado pela higienização, a sessão é criada
     * com o pseudónimo por omissão. Em nenhum caso é exigida autenticação:
     * a sessão e todos os eventos subsequentes são gravados sem login.
     *
     * Devolve o UUID da sessão para o front-end armazenar e usar nas chamadas
     * seguintes.
     */
    @PostMapping("/api/sessions")
    public ResponseEntity<SessionResponseDTO> createSession(
            @Valid @RequestBody(required = false) SessionStartRequestDTO request) {

        String nickname = (request != null) ? request.nickname() : null;
        GameSession session = sessionService.createSession(nickname);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new SessionResponseDTO(
                        session.getId(),
                        session.getPlayerNickname(),
                        "Sessão criada com sucesso."));
    }

    /**
     * POST /api/sessions/{id}/finish
     * Encerra a sessão: define endedAt, calcula o desfecho final e persiste.
     * Chamado pelo front-end quando o jogador chega ao ecrã de fim de jogo.
     */
    @PostMapping("/api/sessions/{id}/finish")
    public ResponseEntity<SessionFinishResponseDTO> finishSession(@PathVariable UUID id) {
        SessionFinishResponseDTO result = sessionService.finishSession(id);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/sessions/{id}
     * Devolve o estado atual de uma sessão.
     *
     * Usado pelo front-end para validar um sessionId guardado localmente antes
     * de retomar uma partida interrompida (recarregamento da página, fecho
     * acidental do separador). Público: o UUID funciona como segredo partilhado
     * entre o servidor e quem iniciou a sessão.
     *
     * Devolve 404 se a sessão não existir.
     */
    @GetMapping("/api/sessions/{id}")
    public ResponseEntity<SessionStateDTO> getSession(@PathVariable UUID id) {
        GameSession session = sessionService.getSessionById(id);
        return ResponseEntity.ok(new SessionStateDTO(
                session.getId(),
                session.getPlayerNickname(),
                session.getSafeSupportScore(),
                session.getFinalReached(),
                session.getStartedAt()
        ));
    }

    /**
     * GET /api/ranking?limit=10
     * Ranking público das sessões concluídas.
     *
     * Ordenado por Pontuação de Apoio Seguro decrescente; em caso de empate,
     * a sessão mais antiga aparece primeiro. Endpoint público: é mostrado ao
     * jogador no ecrã de fim de jogo, sem exigir autenticação.
     *
     * Não expõe o UUID das sessões, apenas o pseudónimo e a pontuação.
     */
    @GetMapping("/api/ranking")
    public ResponseEntity<List<RankingEntryDTO>> getRanking(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(sessionService.getRanking(limit));
    }

    /**
     * GET /api/admin/stats
     * Devolve estatísticas agregadas de todas as sessões.
     * Requer autenticação com papel ADMIN (HTTP Basic).
     */
    @GetMapping("/api/admin/stats")
    public ResponseEntity<AdminStatsDTO> getStats() {
        return ResponseEntity.ok(sessionService.getStats());
    }

    /**
     * GET /api/admin/sessions?page=0&size=20
     * Histórico paginado de sessões, da mais recente para a mais antiga.
     *
     * Inclui sessões por concluir (IN_PROGRESS), o que permite ao docente
     * identificar partidas abandonadas a meio.
     * Requer autenticação com papel ADMIN (HTTP Basic).
     */
    @GetMapping("/api/admin/sessions")
    public ResponseEntity<SessionHistoryDTO> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(sessionService.getHistory(page, size));
    }
}
