package com.caio.escapekind.service;

import com.caio.escapekind.dto.AdminStatsDTO;
import com.caio.escapekind.dto.RankingEntryDTO;
import com.caio.escapekind.dto.SessionFinishResponseDTO;
import com.caio.escapekind.dto.SessionHistoryDTO;
import com.caio.escapekind.dto.SessionSummaryDTO;
import com.caio.escapekind.exception.SessionNotFoundException;
import com.caio.escapekind.model.GameSession;
import com.caio.escapekind.repository.GameSessionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GameSessionService {

    /** Comprimento maximo do pseudonimo publico. */
    private static final int NICKNAME_MAX_LENGTH = 20;

    /** Numero de entradas devolvidas pelo ranking publico quando nao e indicado limite. */
    private static final int DEFAULT_RANKING_LIMIT = 10;

    /** Limite superior aceite para o parametro de ranking, evitando pedidos abusivos. */
    private static final int MAX_RANKING_LIMIT = 50;

    /** Tamanho maximo de pagina do historico. */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Caracteres permitidos no pseudonimo: letras, digitos, espaco,
     * hifen, underscore e ponto. Tudo o resto e removido.
     * Impede injecao de marcacao HTML no ranking publico.
     */
    private static final Pattern NICKNAME_ALLOWED = Pattern.compile("[^\\p{L}\\p{N} ._-]");

    /**
     * Termos bloqueados no pseudonimo.
     *
     * Num jogo cujo tema e o bullying, um ranking publico com texto livre
     * seria um vetor evidente de agressao entre colegas. A verificacao e
     * deliberadamente simples e nao pretende ser exaustiva: funciona como
     * primeira barreira, complementada pela possibilidade de o administrador
     * consultar o historico completo no painel.
     */
    private static final List<String> BLOCKED_TERMS = List.of(
            "admin", "administrador", "root", "escapekind",
            "idiota", "burro", "estupido", "otario", "cabrao", "merda",
            "gordo", "gorda", "paneleiro", "puta", "foda", "caralho"
    );

    private final GameSessionRepository sessionRepository;

    public GameSessionService(GameSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    // ─── Ciclo de vida da sessao ────────────────────────────────────────────

    /**
     * Cria uma sessao anonima sem pseudonimo.
     * Mantida por compatibilidade com o codigo e testes existentes.
     */
    public GameSession createSession() {
        return createSession(null);
    }

    /**
     * Cria uma sessao anonima, opcionalmente com pseudonimo.
     *
     * Nao ha autenticacao envolvida: a sessao e identificada apenas pelo
     * UUID gerado pelo servidor, devolvido ao cliente para as chamadas
     * seguintes. O pseudonimo serve exclusivamente para apresentacao no
     * ranking e e higienizado antes de ser persistido.
     */
    public GameSession createSession(String rawNickname) {
        GameSession session = new GameSession();
        session.setPlayerNickname(sanitizeNickname(rawNickname));
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

    // ─── Pseudonimo ─────────────────────────────────────────────────────────

    /**
     * Higieniza o pseudonimo indicado pelo jogador.
     *
     * Passos:
     *   1. Trata null e texto em branco como ausencia de pseudonimo
     *   2. Normaliza espacos consecutivos
     *   3. Remove caracteres fora do conjunto permitido
     *   4. Trunca ao comprimento maximo
     *   5. Rejeita termos bloqueados
     *
     * Devolve sempre um valor utilizavel: em caso de rejeicao, o pseudonimo
     * por omissao. O jogo nunca e interrompido por causa do pseudonimo.
     */
    public String sanitizeNickname(String rawNickname) {
        if (rawNickname == null) {
            return GameSession.DEFAULT_NICKNAME;
        }

        String cleaned = NICKNAME_ALLOWED.matcher(rawNickname).replaceAll("")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isEmpty()) {
            return GameSession.DEFAULT_NICKNAME;
        }

        if (cleaned.length() > NICKNAME_MAX_LENGTH) {
            cleaned = cleaned.substring(0, NICKNAME_MAX_LENGTH).trim();
        }

        if (containsBlockedTerm(cleaned)) {
            return GameSession.DEFAULT_NICKNAME;
        }

        return cleaned;
    }

    /**
     * Verifica se o pseudonimo contem algum termo bloqueado, ignorando
     * maiusculas e acentuacao (para que "estúpido" e "ESTUPIDO" sejam
     * ambos detetados).
     */
    private boolean containsBlockedTerm(String nickname) {
        String folded = Normalizer.normalize(nickname, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return BLOCKED_TERMS.stream().anyMatch(folded::contains);
    }

    /**
     * Pseudonimo a apresentar na interface.
     *
     * As sessoes criadas antes da introducao do campo player_nickname tem o
     * valor a null na base de dados (a coluna foi acrescentada por evolucao
     * automatica do esquema). Esta funcao garante que essas sessoes aparecem
     * no ranking e no historico com o pseudonimo por omissao, em vez de um
     * campo vazio.
     */
    private String displayNickname(GameSession session) {
        String nickname = session.getPlayerNickname();
        return (nickname == null || nickname.isBlank())
                ? GameSession.DEFAULT_NICKNAME
                : nickname;
    }

    // ─── Ranking ────────────────────────────────────────────────────────────

    /**
     * Devolve o ranking das sessoes concluidas.
     *
     * Ordenacao: pontuacao decrescente e, em caso de empate, a sessao mais
     * antiga primeiro. Sessoes por concluir sao excluidas.
     *
     * @param limit numero de entradas pretendido; valores invalidos sao
     *              substituidos pelo limite por omissao e o maximo e limitado
     */
    @Transactional(readOnly = true)
    public List<RankingEntryDTO> getRanking(Integer limit) {
        int effectiveLimit = (limit == null || limit <= 0) ? DEFAULT_RANKING_LIMIT : limit;
        effectiveLimit = Math.min(effectiveLimit, MAX_RANKING_LIMIT);

        Pageable pageable = PageRequest.of(0, effectiveLimit);
        List<GameSession> sessions = sessionRepository.findRanking(pageable);

        List<RankingEntryDTO> ranking = new ArrayList<>(sessions.size());
        int position = 1;
        for (GameSession session : sessions) {
            ranking.add(new RankingEntryDTO(
                    position++,
                    displayNickname(session),
                    session.getSafeSupportScore() != null ? session.getSafeSupportScore() : 0,
                    session.getFinalReached(),
                    session.getDurationSeconds(),
                    session.getStartedAt()
            ));
        }
        return ranking;
    }

    // ─── Historico ──────────────────────────────────────────────────────────

    /**
     * Devolve uma pagina do historico de sessoes, da mais recente para a mais
     * antiga, incluindo sessoes por concluir.
     *
     * A contagem de eventos de cada sessao e obtida numa unica consulta
     * agregada, evitando o problema N+1.
     */
    @Transactional(readOnly = true)
    public SessionHistoryDTO getHistory(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        Page<GameSession> sessionPage =
                sessionRepository.findAllByOrderByStartedAtDesc(PageRequest.of(safePage, safeSize));

        List<UUID> ids = sessionPage.getContent().stream()
                .map(GameSession::getId)
                .collect(Collectors.toList());

        Map<UUID, Long> eventCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : sessionRepository.countEventsBySessionIds(ids)) {
                eventCounts.put((UUID) row[0], (Long) row[1]);
            }
        }

        List<SessionSummaryDTO> summaries = sessionPage.getContent().stream()
                .map(s -> new SessionSummaryDTO(
                        s.getId(),
                        displayNickname(s),
                        s.getStartedAt(),
                        s.getEndedAt(),
                        s.getDurationSeconds(),
                        s.getSafeSupportScore(),
                        s.getFinalReached(),
                        eventCounts.getOrDefault(s.getId(), 0L)
                ))
                .collect(Collectors.toList());

        return new SessionHistoryDTO(
                summaries,
                sessionPage.getNumber(),
                sessionPage.getSize(),
                sessionPage.getTotalElements(),
                sessionPage.getTotalPages()
        );
    }

    // ─── Estatisticas agregadas ─────────────────────────────────────────────

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
