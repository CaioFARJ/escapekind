package com.caio.escapekind.service;

import com.caio.escapekind.dto.AdminStatsDTO;
import com.caio.escapekind.dto.SessionFinishResponseDTO;
import com.caio.escapekind.model.GameSession;
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

    /**
     * Cria e persiste uma nova sessão de jogo anónima.
     */
    public GameSession createSession() {
        GameSession session = new GameSession();
        return sessionRepository.save(session);
    }

    /**
     * Obtém uma sessão pelo UUID. Lança exceção se não existir.
     */
    public GameSession getSessionById(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sessão não encontrada: " + id));
    }

    /**
     * Persiste uma sessão já existente (usada após atualização de pontuação).
     */
    public GameSession save(GameSession session) {
        return sessionRepository.save(session);
    }

    /**
     * Encerra uma sessão de jogo: define endedAt e calcula o desfecho final
     * com base na pontuação acumulada.
     *
     * Regras de desfecho:
     *   >= 7 pontos → POSITIVE  (espectador ativo e eficaz)
     *   >= 3 pontos → NEUTRAL   (alguma intervenção, mas insuficiente)
     *   < 3 pontos  → NEGATIVE  (passividade ou agravamento)
     */
    public SessionFinishResponseDTO finishSession(UUID id) {
        GameSession session = getSessionById(id);

        if (!"IN_PROGRESS".equals(session.getFinalReached())) {
            // Sessão já encerrada — devolve o estado atual sem alterar
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

    /**
     * Calcula o desfecho narrativo com base na Pontuação de Apoio Seguro.
     *
     * A pontuação máxima teórica é 9 (3 escolhas × 3 pontos).
     * Os limiares foram definidos para distinguir três perfis de intervenção.
     */
    public String resolveFinal(int totalScore) {
        if (totalScore >= 7) return "POSITIVE";
        if (totalScore >= 3) return "NEUTRAL";
        return "NEGATIVE";
    }

    /**
     * Calcula estatísticas agregadas de todas as sessões para o painel de administração.
     */
    public AdminStatsDTO getStats() {
        long total = sessionRepository.count();
        long completed = sessionRepository.findAll().stream()
                .filter(s -> !"IN_PROGRESS".equals(s.getFinalReached()))
                .count();
        Double avg = sessionRepository.averageSafeSupportScore();

        // Agrupa contagens por desfecho final
        List<Object[]> rawCounts = sessionRepository.countByFinalReached();
        Map<String, Long> distribution = new HashMap<>();
        for (Object[] row : rawCounts) {
            distribution.put((String) row[0], (Long) row[1]);
        }

        return new AdminStatsDTO(total, completed, avg != null ? avg : 0.0, distribution);
    }
}
