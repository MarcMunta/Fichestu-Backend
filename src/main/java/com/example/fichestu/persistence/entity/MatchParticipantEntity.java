package com.example.fichestu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "match_participants")
public class MatchParticipantEntity {

    @EmbeddedId
    private MatchParticipantId id = new MatchParticipantId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("matchId")
    @JoinColumn(name = "match_id", nullable = false)
    private GameSessionEntity match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "selected_ball_number")
    private Integer selectedBallNumber;

    @Column(name = "multiplier_won", nullable = false, precision = 10, scale = 2)
    private BigDecimal multiplierWon = BigDecimal.ONE;

    @Column(name = "current_hp", nullable = false)
    private Integer currentHp = 50;

    @Column(name = "is_alive", nullable = false)
    private Boolean alive = Boolean.TRUE;

    @PrePersist
    public void prePersist() {
        if (multiplierWon == null) {
            multiplierWon = BigDecimal.ONE;
        }
        if (currentHp == null) {
            currentHp = 50;
        }
        if (alive == null) {
            alive = Boolean.TRUE;
        }
    }

    public MatchParticipantId getId() {
        return id;
    }

    public void setId(MatchParticipantId id) {
        this.id = id;
    }

    public GameSessionEntity getMatch() {
        return match;
    }

    public void setMatch(GameSessionEntity match) {
        this.match = match;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public Integer getSelectedBallNumber() {
        return selectedBallNumber;
    }

    public void setSelectedBallNumber(Integer selectedBallNumber) {
        this.selectedBallNumber = selectedBallNumber;
    }

    public BigDecimal getMultiplierWon() {
        return multiplierWon;
    }

    public void setMultiplierWon(BigDecimal multiplierWon) {
        this.multiplierWon = multiplierWon;
    }

    public Integer getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(Integer currentHp) {
        this.currentHp = currentHp;
    }

    public Boolean getAlive() {
        return alive;
    }

    public void setAlive(Boolean alive) {
        this.alive = alive;
    }
}
