package com.caio.escapekind.service;

import com.caio.escapekind.dto.EventRequestDTO;
import com.caio.escapekind.dto.EventResponseDTO;
import com.caio.escapekind.model.GameEvent;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.repository.GameEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes unitários da camada de serviço de eventos.
 *
 * Cobertura:
 *  - Tabela de pontuação por tipo de escolha
 *  - Cálculo da Pontuação de Apoio Seguro acumulada
 *  - Registo de evento em sessão válida
 *  - Erro ao tentar registar evento em sessão já encerrada
 */
@ExtendWith(MockitoExtension.class)
class GameEventServiceTest {

    @Mock
    private GameEventRepository eventRepository;

    @Mock
    private GameSessionService sessionService;

    @InjectMocks
    private GameEventService gameEventService;

    private GameSession mockSession;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        mockSession = new GameSession();
        // Simula o @PrePersist
        mockSession.setSafeSupportScore(0);
        mockSession.setFinalReached("IN_PROGRESS");
    }

    // ─── Tabela de pontuação ──────────────────────────────────────────────────

    @Test
    @DisplayName("DEFEND deve valer 3 pontos")
    void calculatePoints_defend_returns3() {
        assertThat(gameEventService.calculatePoints("DEFEND")).isEqualTo(3);
    }

    @Test
    @DisplayName("SUPPORT deve valer 2 pontos")
    void calculatePoints_support_returns2() {
        assertThat(gameEventService.calculatePoints("SUPPORT")).isEqualTo(2);
    }

    @Test
    @DisplayName("REPORT deve valer 1 ponto")
    void calculatePoints_report_returns1() {
        assertThat(gameEventService.calculatePoints("REPORT")).isEqualTo(1);
    }

    @Test
    @DisplayName("IGNORE deve valer 0 pontos")
    void calculatePoints_ignore_returns0() {
        assertThat(gameEventService.calculatePoints("IGNORE")).isEqualTo(0);
    }

    @Test
    @DisplayName("Escolha desconhecida deve valer 0 pontos")
    void calculatePoints_unknown_returns0() {
        assertThat(gameEventService.calculatePoints("ANYTHING")).isEqualTo(0);
        assertThat(gameEventService.calculatePoints(null)).isEqualTo(0);
    }

    @Test
    @DisplayName("calculatePoints deve ser case-insensitive")
    void calculatePoints_caseInsensitive() {
        assertThat(gameEventService.calculatePoints("defend")).isEqualTo(3);
        assertThat(gameEventService.calculatePoints("Defend")).isEqualTo(3);
        assertThat(gameEventService.calculatePoints("support")).isEqualTo(2);
    }

    // ─── Registo de evento ───────────────────────────────────────────────────

    @Test
    @DisplayName("Deve registar evento e acumular pontuação corretamente")
    void processAndSaveEvent_accumulatesScore() {
        mockSession.setSafeSupportScore(2); // já havia 2 pontos
        when(sessionService.getSessionById(sessionId)).thenReturn(mockSession);
        GameEvent saved = new GameEvent();
        saved.setPointsAwarded(3);
        // Usa reflexão para simular ID gerado
        try {
            var f = GameEvent.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(saved, 1L);
        } catch (Exception ignored) {}

        when(eventRepository.save(any(GameEvent.class))).thenReturn(saved);
        when(sessionService.save(any(GameSession.class))).thenReturn(mockSession);

        EventRequestDTO req = new EventRequestDTO(sessionId, "c1_s1", "DEFEND", "NARRATIVE_CHOICE");
        EventResponseDTO resp = gameEventService.processAndSaveEvent(req);

        // 2 pontos anteriores + 3 da escolha DEFEND = 5
        assertThat(resp.totalScore()).isEqualTo(5);
    }

    @Test
    @DisplayName("Não deve registar evento em sessão encerrada")
    void processAndSaveEvent_throwsWhenSessionFinished() {
        mockSession.setFinalReached("POSITIVE"); // sessão já encerrada
        when(sessionService.getSessionById(sessionId)).thenReturn(mockSession);

        EventRequestDTO req = new EventRequestDTO(sessionId, "c1_s1", "DEFEND", "NARRATIVE_CHOICE");

        assertThatThrownBy(() -> gameEventService.processAndSaveEvent(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encerrada");
    }
}
