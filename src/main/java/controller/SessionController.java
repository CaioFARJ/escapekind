package com.caio.escapekind.controller;

import com.caio.escapekind.dto.SessionResponseDTO;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.service.GameSessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions")
@CrossOrigin(origins = "*")
public class SessionController {

    private final GameSessionService sessionService;

    public SessionController(GameSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<SessionResponseDTO> createSession() {
        GameSession session = sessionService.createSession();

        SessionResponseDTO response = new SessionResponseDTO(
                session.getId(),
                "Sessão criada com sucesso"
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}