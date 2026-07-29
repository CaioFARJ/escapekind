package com.caio.escapekind.service;

import com.caio.escapekind.dto.AdminStatsDTO;
import com.caio.escapekind.dto.SessionFinishResponseDTO;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.exception.SessionNotFoundException;
import com.caio.escapekind.repository.GameSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                .orElseThrow(() -> new SessionNotFoundException(id));
    }

    public GameSession save(GameSession session) {
        return sessionRepository.save(session);
    }

    public SessionFinishResponseDTO finishSession(UUID id) {
        GameSession session = getSessionById(id);

        if (!"IN_PROGRESS".equals(session.getFinalReached())) {
            return new SessionFinishResponseDTO(
                    session.getId(),
                    session.getSafeSupportScore(),
                    session.getFinalReached(),
                    session.getEndedAt(),
                    "Sessão já tinha sido encerrada anteriormente."
            );
        }

        session.setEndedAt(LocalDateTime.now());
        session.setFinalReached(resolveFinal(session.getSafeSupportScore()));
        GameSession saved = sessionRepository.save(session);

        return new SessionFinishResponseDTO(
                saved.getId(),
                saved.getSafeSupportScore(),
                saved.getFinalReached(),
                saved.getEndedAt(),
                "Sessão encerrada com sucesso."
        );
    }

    public String resolveFinal(int totalScore) {
        if (totalScore >= 7) return "POSITIVE";
        if (totalScore >= 3) return "NEUTRAL";
        return "NEGATIVE";
    }

    public AdminStatsDTO getStats() {
        long total = sessionRepository.count();
        long completed = sessionRepository.findAll().stream()
                .filter(s -> !"IN_PROGRESS".equals(s.getFinalReached()))
                .count();
        Double avg = sessionRepository.averageSafeSupportScore();

        List<Object[]> rawCounts = sessionRepository.countByFinalReached();
        Map<String, Long> distribution = new HashMap<>();
        for (Object[] row : rawCounts) {
            distribution.put((String) row[0], (Long) row[1]);
        }

        return new AdminStatsDTO(total, completed, avg != null ? avg : 0.0, distribution);
    }
}