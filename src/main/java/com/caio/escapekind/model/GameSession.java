package com.caio.escapekind.model;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sessao de jogo do EscapeKind.
 *
 * Cada sessao representa uma partida completa de um jogador anonimo.
 * Nao existe qualquer ligacao a uma conta de utilizador: a identidade do
 * jogador resume-se ao UUID gerado pelo servidor e a um pseudonimo opcional
 * (playerNickname) escolhido no ecra inicial. Esta opcao garante que os dados
 * de jogo sao recolhidos sem exigir registo nem autenticacao, mantendo o
 * anonimato exigido pelo contexto escolar da aplicacao.
 */
@Entity
@Table(name = "game_sessions")
public class GameSession {

    /** Pseudonimo atribuido quando o jogador nao indica nenhum. */
    public static final String DEFAULT_NICKNAME = "Anónimo";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Pseudonimo publico do jogador, usado no ranking.
     * Nao constitui dado pessoal: e livremente escolhido, opcional e
     * nao esta associado a qualquer conta ou identificador real.
     */
    @Column(name = "player_nickname", length = 20)
    private String playerNickname;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "safe_support_score")
    private Integer safeSupportScore;

    @Column(name = "final_reached")
    private String finalReached;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameEvent> events = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.startedAt = LocalDateTime.now();
        if (this.safeSupportScore == null) {
            this.safeSupportScore = 0;
        }
        if (this.finalReached == null) {
            this.finalReached = "IN_PROGRESS";
        }
        if (this.playerNickname == null || this.playerNickname.isBlank()) {
            this.playerNickname = DEFAULT_NICKNAME;
        }
    }

    /**
     * Duracao da sessao em segundos.
     * Devolve null enquanto a sessao nao estiver encerrada.
     * Campo calculado (nao persistido) para evitar redundancia na base de dados.
     */
    @Transient
    public Long getDurationSeconds() {
        if (startedAt == null || endedAt == null) return null;
        return Duration.between(startedAt, endedAt).getSeconds();
    }

    // Getters e Setters
    public UUID getId() { return id; }

    public String getPlayerNickname() { return playerNickname; }
    public void setPlayerNickname(String playerNickname) { this.playerNickname = playerNickname; }

    public LocalDateTime getStartedAt() { return startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public Integer getSafeSupportScore() { return safeSupportScore; }
    public void setSafeSupportScore(Integer safeSupportScore) { this.safeSupportScore = safeSupportScore; }

    public String getFinalReached() { return finalReached; }
    public void setFinalReached(String finalReached) { this.finalReached = finalReached; }

    public List<GameEvent> getEvents() { return events; }
    public void setEvents(List<GameEvent> events) { this.events = events; }
}
