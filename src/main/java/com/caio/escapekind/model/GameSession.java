package com.caio.escapekind.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

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
    }

    public UUID getId() {
        return id;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public Integer getSafeSupportScore() {
        return safeSupportScore;
    }

    public void setSafeSupportScore(Integer safeSupportScore) {
        this.safeSupportScore = safeSupportScore;
    }

    public String getFinalReached() {
        return finalReached;
    }

    public void setFinalReached(String finalReached) {
        this.finalReached = finalReached;
    }

    public List<GameEvent> getEvents() {
        return events;
    }

    public void setEvents(List<GameEvent> events) {
        this.events = events;
    }
}