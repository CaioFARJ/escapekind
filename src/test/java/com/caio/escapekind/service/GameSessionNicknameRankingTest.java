package com.caio.escapekind.service;

import com.caio.escapekind.dto.RankingEntryDTO;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.repository.GameSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios da identificacao anonima do jogador e do ranking.
 *
 * Cobertura:
 *  - Higienizacao do pseudonimo (ausente, vazio, marcacao HTML, comprimento,
 *    termos bloqueados, acentuacao)
 *  - Construcao do ranking a partir das sessoes devolvidas pelo repositorio
 *  - Limites do parametro de dimensao do ranking
 *
 * Estes testes nao tocam na base de dados: o repositorio e simulado com
 * Mockito, isolando a logica de negocio da camada de persistencia.
 */
@ExtendWith(MockitoExtension.class)
class GameSessionNicknameRankingTest {

    @Mock
    private GameSessionRepository sessionRepository;

    @InjectMocks
    private GameSessionService sessionService;

    // ─── Higienizacao do pseudonimo ──────────────────────────────────────────

    @Test
    @DisplayName("Pseudonimo ausente ou em branco assume o valor por omissao")
    void sanitizeNickname_ausente() {
        assertThat(sessionService.sanitizeNickname(null)).isEqualTo("Anónimo");
        assertThat(sessionService.sanitizeNickname("")).isEqualTo("Anónimo");
        assertThat(sessionService.sanitizeNickname("     ")).isEqualTo("Anónimo");
    }

    @Test
    @DisplayName("Pseudonimo valido e preservado, com espacos normalizados")
    void sanitizeNickname_valido() {
        assertThat(sessionService.sanitizeNickname("Rita")).isEqualTo("Rita");
        assertThat(sessionService.sanitizeNickname("  Joao   Pedro  ")).isEqualTo("Joao Pedro");
        assertThat(sessionService.sanitizeNickname("Ana_92")).isEqualTo("Ana_92");
        assertThat(sessionService.sanitizeNickname("Sofía")).isEqualTo("Sofía");
    }

    @Test
    @DisplayName("Marcacao HTML e removida do pseudonimo")
    void sanitizeNickname_removeMarcacao() {
        assertThat(sessionService.sanitizeNickname("<b>Rita</b>")).isEqualTo("bRitab");
        assertThat(sessionService.sanitizeNickname("<script>alert(1)</script>"))
                .doesNotContain("<", ">", "(", ")");
    }

    @Test
    @DisplayName("Pseudonimo acima de 20 caracteres e truncado")
    void sanitizeNickname_truncado() {
        String longo = "AAAAAAAAAABBBBBBBBBBCCCCC"; // 25 caracteres
        assertThat(sessionService.sanitizeNickname(longo)).hasSize(20);
    }

    @Test
    @DisplayName("Termos bloqueados sao substituidos pelo valor por omissao")
    void sanitizeNickname_termosBloqueados() {
        assertThat(sessionService.sanitizeNickname("admin")).isEqualTo("Anónimo");
        assertThat(sessionService.sanitizeNickname("O Burro do Ze")).isEqualTo("Anónimo");
    }

    @Test
    @DisplayName("Deteção de termos bloqueados ignora acentos e maiusculas")
    void sanitizeNickname_termosBloqueadosNormalizados() {
        assertThat(sessionService.sanitizeNickname("ESTÚPIDO")).isEqualTo("Anónimo");
        assertThat(sessionService.sanitizeNickname("estupido")).isEqualTo("Anónimo");
    }

    // ─── Criacao de sessao ───────────────────────────────────────────────────

    @Test
    @DisplayName("Sessao criada sem pseudonimo fica com o valor por omissao")
    void createSession_semPseudonimo() {
        when(sessionRepository.save(any(GameSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GameSession session = sessionService.createSession(null);

        assertThat(session.getPlayerNickname()).isEqualTo("Anónimo");
    }

    @Test
    @DisplayName("Sessao criada com pseudonimo guarda o valor higienizado")
    void createSession_comPseudonimo() {
        when(sessionRepository.save(any(GameSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GameSession session = sessionService.createSession("  <i>Rita</i>  ");

        assertThat(session.getPlayerNickname()).isEqualTo("iRitai");
    }

    // ─── Ranking ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ranking numera as posicoes pela ordem devolvida pelo repositorio")
    void getRanking_numeraPosicoes() {
        when(sessionRepository.findRanking(any(Pageable.class)))
                .thenReturn(List.of(
                        sessaoConcluida("Rita", 9),
                        sessaoConcluida("Tiago", 7),
                        sessaoConcluida("Anónimo", 4)
                ));

        List<RankingEntryDTO> ranking = sessionService.getRanking(10);

        assertThat(ranking).hasSize(3);
        assertThat(ranking.get(0).position()).isEqualTo(1);
        assertThat(ranking.get(0).nickname()).isEqualTo("Rita");
        assertThat(ranking.get(2).position()).isEqualTo(3);
    }

    @Test
    @DisplayName("Ranking vazio devolve lista vazia, nao null")
    void getRanking_vazio() {
        when(sessionRepository.findRanking(any(Pageable.class))).thenReturn(List.of());

        assertThat(sessionService.getRanking(10)).isEmpty();
    }

    @Test
    @DisplayName("Limite invalido ou excessivo e corrigido sem lancar excecao")
    void getRanking_limitesCorrigidos() {
        when(sessionRepository.findRanking(any(Pageable.class))).thenReturn(List.of());

        assertThat(sessionService.getRanking(null)).isEmpty();
        assertThat(sessionService.getRanking(0)).isEmpty();
        assertThat(sessionService.getRanking(-5)).isEmpty();
        assertThat(sessionService.getRanking(9999)).isEmpty();
    }

    // ─── Auxiliares ──────────────────────────────────────────────────────────

    private GameSession sessaoConcluida(String nickname, int score) {
        GameSession session = new GameSession();
        session.setPlayerNickname(nickname);
        session.setSafeSupportScore(score);
        session.setFinalReached(score >= 7 ? "POSITIVE" : "NEUTRAL");
        return session;
    }
}
