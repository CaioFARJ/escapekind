package com.caio.escapekind.service;

import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.repository.GameSessionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GameSessionService {

    private final GameSessionRepository sessionRepository;

    public GameSessionService(GameSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public GameSession createSession() {
        GameSession session = new GameSession();
        return sessionRepository.save(session);
    }

    public GameSession getSessionById(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sessão não encontrada."));
    }

    public GameSession save(GameSession session) {
        return sessionRepository.save(session);
    }
}