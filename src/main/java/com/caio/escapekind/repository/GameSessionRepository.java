package com.caio.escapekind.repository;

import com.caio.escapekind.model.GameSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    /**
     * Conta sessoes agrupadas pelo desfecho final.
     * Retorna pares [finalReached, count].
     */
    @Query("SELECT s.finalReached, COUNT(s) FROM GameSession s GROUP BY s.finalReached")
    List<Object[]> countByFinalReached();

    /**
     * Calcula a media da Pontuacao de Apoio Seguro de todas as sessoes concluidas.
     */
    @Query("SELECT AVG(s.safeSupportScore) FROM GameSession s WHERE s.finalReached != 'IN_PROGRESS'")
    Double averageSafeSupportScore();

    /**
     * Ranking das sessoes concluidas.
     *
     * Criterio de ordenacao:
     *   1. Pontuacao de Apoio Seguro, decrescente
     *   2. Em caso de empate, a sessao mais antiga fica a frente (startedAt ascendente)
     *
     * Sessoes por concluir (IN_PROGRESS) sao excluidas para evitar que
     * partidas abandonadas a meio apareçam no ranking.
     *
     * O numero de entradas devolvidas e controlado pelo Pageable.
     */
    @Query("SELECT s FROM GameSession s WHERE s.finalReached <> 'IN_PROGRESS' "
         + "ORDER BY s.safeSupportScore DESC, s.startedAt ASC")
    List<GameSession> findRanking(Pageable pageable);

    /**
     * Historico paginado de todas as sessoes, da mais recente para a mais antiga.
     * Inclui sessoes por concluir, uteis para deteção de abandono.
     */
    Page<GameSession> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * Numero de eventos por sessao, para um conjunto de sessoes.
     * Retorna pares [sessionId, count].
     *
     * Executada uma unica vez por pagina de historico, evitando o problema
     * N+1 que resultaria de percorrer a colecao lazy de cada sessao.
     */
    @Query("SELECT e.session.id, COUNT(e) FROM GameEvent e "
         + "WHERE e.session.id IN :sessionIds GROUP BY e.session.id")
    List<Object[]> countEventsBySessionIds(List<UUID> sessionIds);
}
