package com.caio.escapekind.repository;

import com.caio.escapekind.model.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {

    /**
     * Conta sessões agrupadas pelo desfecho final.
     * Retorna pares [finalReached, count].
     */
    @Query("SELECT s.finalReached, COUNT(s) FROM GameSession s GROUP BY s.finalReached")
    List<Object[]> countByFinalReached();

    /**
     * Calcula a média da Pontuação de Apoio Seguro de todas as sessões concluídas.
     */
    @Query("SELECT AVG(s.safeSupportScore) FROM GameSession s WHERE s.finalReached != 'IN_PROGRESS'")
    Double averageSafeSupportScore();
}
