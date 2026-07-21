package com.caio.escapekind.service;

import com.caio.escapekind.dto.SessionFinishResponseDTO;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.repository.GameSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes unitários da camada de serviço de sessões.
 *
 * Cobertura:
 *  - Limiares de desfecho final (POSITIVE / NEUTRAL / NEGATIVE)
 *  - Encerramento de sessão em curso
 *  - Encerramento idempotente de sessão já encerrada
 *  - Erro ao encerrar sessão inexistente
 */
@ExtendWith(MockitoExtension.class)
class GameSessionServiceTest {

    @Mock
    private GameSessionRepository sessionRepository;

    @InjectMocks
    private GameSessionService sessionService;

    private UUID sessionId;
    private GameSession session;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
        session = new GameSession();
        session.setSafeSupportScore(0);
        session.setFinalReached("IN_PROGRESS");
    }

    // ─── resolveFinal ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Score >= 7 → POSITIVE")
    void resolveFinal_positive() {
        assertThat(sessionService.resolveFinal(7)).isEqualTo("POSITIVE");
        assertThat(sessionService.resolveFinal(9)).isEqualTo("POSITIVE");
    }

    @Test
    @DisplayName("Score entre 3 e 6 → NEUTRAL")
    void resolveFinal_neutral() {
        assertThat(sessionService.resolveFinal(3)).isEqualTo("NEUTRAL");
        assertThat(sessionService.resolveFinal(6)).isEqualTo("NEUTRAL");
    }

    @Test
    @DisplayName("Score < 3 → NEGATIVE")
    void resolveFinal_negative() {
        assertThat(sessionService.resolveFinal(0)).isEqualTo("NEGATIVE");
        assertThat(sessionService.resolveFinal(2)).isEqualTo("NEGATIVE");
    }

    // ─── finishSession ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Deve encerrar sessão em curso e calcular o desfecho correto")
    void finishSession_setsEndedAtAndFinal() {
        session.setSafeSupportScore(8);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(GameSession.class))).thenReturn(session);

        SessionFinishResponseDTO result = sessionService.finishSession(sessionId);

        assertThat(result.finalScore()).isEqualTo(8);
        assertThat(result.finalReached()).isEqualTo("POSITIVE");
        assertThat(result.endedAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve retornar estado atual sem modificar sessão já encerrada")
    void finishSession_idempotentIfAlreadyFinished() {
        session.setSafeSupportScore(5);
        session.setFinalReached("NEUTRAL");

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionFinishResponseDTO result = sessionService.finishSession(sessionId);

        assertThat(result.finalReached()).isEqualTo("NEUTRAL");
        assertThat(result.message()).contains("já tinha sido encerrada");
    }

    @Test
    @DisplayName("Deve lançar exceção para sessão inexistente")
    void getSessionById_throwsForUnknownId() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSessionById(sessionId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("não encontrada");
    }
}
