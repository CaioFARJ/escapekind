package com.caio.escapekind.service;

import com.caio.escapekind.dto.EventRequestDTO;
import com.caio.escapekind.dto.EventResponseDTO;
import com.caio.escapekind.model.GameEvent;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.repository.GameEventRepository;
import org.springframework.stereotype.Service;

@Service
public class GameEventService {

    private final GameEventRepository eventRepository;
    private final GameSessionService sessionService;

    public GameEventService(GameEventRepository eventRepository, GameSessionService sessionService) {
        this.eventRepository = eventRepository;
        this.sessionService = sessionService;
    }

    /**
     * Processa uma escolha do jogador:
     *   1. Valida que a sessão existe e está em curso.
     *   2. Calcula os pontos atribuídos à escolha.
     *   3. Persiste o evento.
     *   4. Atualiza a pontuação acumulada na sessão.
     *   5. Devolve a resposta ao front-end.
     */
    public EventResponseDTO processAndSaveEvent(EventRequestDTO request) {
        GameSession session = sessionService.getSessionById(request.sessionId());

        if (!"IN_PROGRESS".equals(session.getFinalReached())) {
            throw new IllegalStateException("Não é possível registar eventos numa sessão já encerrada.");
        }

        int points = calculatePoints(request.choiceMade());

        GameEvent event = new GameEvent();
        event.setSession(session);
        event.setNodeId(request.nodeId());
        event.setChoiceMade(request.choiceMade());
        event.setEventType(request.eventType());
        event.setPointsAwarded(points);

        GameEvent savedEvent = eventRepository.save(event);

        int newTotal = session.getSafeSupportScore() + points;
        session.setSafeSupportScore(newTotal);
        // Nota: o desfecho final só é fixado ao encerrar a sessão (POST /finish).
        // Aqui actualizamos apenas como indicador provisório.
        session.setFinalReached(sessionService.resolveFinal(newTotal));
        sessionService.save(session);

        return new EventResponseDTO(
                savedEvent.getId(),
                newTotal,
                session.getFinalReached(),
                "Evento registado com sucesso."
        );
    }

    /**
     * Tabela de pontuação por tipo de escolha.
     *
     * DEFEND  → 3 pts  (intervenção direta e assertiva)
     * SUPPORT → 2 pts  (apoio à vítima após o incidente)
     * REPORT  → 1 pt   (reporte a adulto ou autoridade)
     * IGNORE  → 0 pts  (inação ou cumplicidade passiva)
     *
     * Esta lógica reside no servidor para impedir manipulação no browser.
     */
    public int calculatePoints(String choiceMade) {
        if (choiceMade == null) return 0;
        return switch (choiceMade.toUpperCase()) {
            case "DEFEND"  -> 3;
            case "SUPPORT" -> 2;
            case "REPORT"  -> 1;
            default        -> 0;
        };
    }
}
