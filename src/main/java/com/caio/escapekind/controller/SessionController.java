package com.caio.escapekind.controller;

import com.caio.escapekind.dto.AdminStatsDTO;
import com.caio.escapekind.dto.SessionFinishResponseDTO;
import com.caio.escapekind.dto.SessionResponseDTO;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.service.GameSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * Devolve o UUID da sessão para o front-end armazenar e usar nas chamadas seguintes.
     */
    @PostMapping("/api/sessions")
    public ResponseEntity<SessionResponseDTO> createSession() {
        GameSession session = sessionService.createSession();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new SessionResponseDTO(session.getId(), "Sessão criada com sucesso."));
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
     * GET /api/admin/stats
     * Devolve estatísticas agregadas de todas as sessões.
     * Requer autenticação com papel ADMIN (HTTP Basic).
     */
    @GetMapping("/api/admin/stats")
    public ResponseEntity<AdminStatsDTO> getStats() {
        return ResponseEntity.ok(sessionService.getStats());
    }
}
