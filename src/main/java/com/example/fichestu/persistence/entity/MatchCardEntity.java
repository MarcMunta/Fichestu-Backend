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

@Entity
@Table(name = "match_cards")
public class MatchCardEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Integer cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private GameSessionEntity match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private UserEntity owner;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "card_value")
    private Integer cardValue;

    @Column(name = "is_used", nullable = false)
    private Boolean used = Boolean.FALSE;

    @PrePersist
    public void prePersist() {
        if (used == null) {
            used = Boolean.FALSE;
        }
    }

    public Integer getCardId() {
        return cardId;
    }

    public void setCardId(Integer cardId) {
        this.cardId = cardId;
    }

    public GameSessionEntity getMatch() {
        return match;
    }

    public void setMatch(GameSessionEntity match) {
        this.match = match;
    }

    public UserEntity getOwner() {
        return owner;
    }

    public void setOwner(UserEntity owner) {
        this.owner = owner;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public Integer getCardValue() {
        return cardValue;
    }

    public void setCardValue(Integer cardValue) {
        this.cardValue = cardValue;
    }

    public Boolean getUsed() {
        return used;
    }

    public void setUsed(Boolean used) {
        this.used = used;
    }
}
