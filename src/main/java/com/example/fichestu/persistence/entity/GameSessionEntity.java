package com.example.fichestu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "game_sessions")
public class GameSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Integer matchId;

    @Column(nullable = false, length = 20)
    private String status = "WAITING";

    @Column(name = "start_time", nullable = false, updatable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private UserEntity winner;

    @PrePersist
    public void prePersist() {
        if (status == null || status.isBlank()) {
            status = "WAITING";
        }
        if (startTime == null) {
            startTime = Instant.now();
        }
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public UserEntity getWinner() {
        return winner;
    }

    public void setWinner(UserEntity winner) {
        this.winner = winner;
    }
}
