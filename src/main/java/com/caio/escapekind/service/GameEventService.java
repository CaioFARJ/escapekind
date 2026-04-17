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
        session.setFinalReached(resolveFinal(newTotal));
        sessionService.save(session);

        return new EventResponseDTO(
                savedEvent.getId(),
                newTotal,
                session.getFinalReached(),
                "Evento registado com sucesso"
        );
    }

    private int calculatePoints(String choiceMade) {
        if (choiceMade == null) {
            return 0;
        }

        return switch (choiceMade.toLowerCase()) {
            case "defend" -> 3;
            case "support" -> 2;
            case "report" -> 1;
            case "ignore" -> 0;
            default -> 0;
        };
    }

    private String resolveFinal(int totalScore) {
        if (totalScore >= 5) {
            return "POSITIVE";
        }
        if (totalScore >= 2) {
            return "NEUTRAL";
        }
        return "NEGATIVE";
    }
}