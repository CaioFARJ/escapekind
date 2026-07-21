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
        // Nota: o desfecho final so e fixado ao encerrar a sessao (POST /finish).
        // O campo finalReached mantem-se "IN_PROGRESS" ate la, para nao bloquear novos eventos.
        sessionService.save(session);

        return new EventResponseDTO(
                savedEvent.getId(),
                newTotal,
                session.getFinalReached(),
                "Evento registado com sucesso."
        );
    }

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