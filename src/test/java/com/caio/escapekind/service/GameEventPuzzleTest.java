package com.caio.escapekind.service;

import com.caio.escapekind.repository.GameEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do enigma "Reunir Provas" na camada de servico.
 *
 * O enigma condiciona o desfecho narrativo do capitulo 3, mas nao altera a
 * Pontuacao de Apoio Seguro. Esta separacao e deliberada: manter a pontuacao
 * ligada apenas as tres decisoes de espetador preserva a escala de 0 a 9 e a
 * leitura pedagogica das suas faixas.
 *
 * A tentativa e registada com um choiceMade proprio (EVIDENCE_FORTE,
 * EVIDENCE_FRACA, EVIDENCE_NENHUMA, seguido dos fragmentos selecionados), que
 * o calculatePoints nao reconhece e por isso avalia em zero pontos pelo ramo
 * por omissao. O evento fica na base de dados para analise, sem contaminar a
 * pontuacao.
 */
@ExtendWith(MockitoExtension.class)
class GameEventPuzzleTest {

    @Mock
    private GameEventRepository eventRepository;

    @Mock
    private GameSessionService sessionService;

    @InjectMocks
    private GameEventService eventService;

    @Test
    @DisplayName("Tentativas do enigma nao atribuem pontos, qualquer que seja o resultado")
    void enigmaNaoPontua() {
        assertThat(eventService.calculatePoints("EVIDENCE_FORTE")).isZero();
        assertThat(eventService.calculatePoints("EVIDENCE_FRACA")).isZero();
        assertThat(eventService.calculatePoints("EVIDENCE_NENHUMA")).isZero();
    }

    @Test
    @DisplayName("O detalhe dos fragmentos selecionados nao altera a pontuacao")
    void detalheDeFragmentosNaoPontua() {
        assertThat(eventService.calculatePoints("EVIDENCE_FORTE|ev_papel,ev_captura")).isZero();
        assertThat(eventService.calculatePoints("EVIDENCE_FRACA|ev_inveja")).isZero();
        assertThat(eventService.calculatePoints("EVIDENCE_NENHUMA|")).isZero();
    }

    @Test
    @DisplayName("Transicoes de ecra nao atribuem pontos")
    void transicoesNaoPontuam() {
        assertThat(eventService.calculatePoints("CONTINUE")).isZero();
    }

    @Test
    @DisplayName("As tres decisoes de espetador mantem a pontuacao original")
    void decisoesMantemPontuacao() {
        assertThat(eventService.calculatePoints("DEFEND")).isEqualTo(3);
        assertThat(eventService.calculatePoints("SUPPORT")).isEqualTo(2);
        assertThat(eventService.calculatePoints("REPORT")).isEqualTo(1);
        assertThat(eventService.calculatePoints("IGNORE")).isZero();
    }

    @Test
    @DisplayName("Pontuacao maxima possivel e 9: tres decisoes de 3 pontos")
    void pontuacaoMaximaSaoNovePontos() {
        int maximo = eventService.calculatePoints("DEFEND") * 3;
        assertThat(maximo).isEqualTo(9);
    }
}
