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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "token_price_history")
public class TokenPriceHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Integer historyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "token_id", nullable = false)
    private TokenEntity token;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public Integer getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Integer historyId) {
        this.historyId = historyId;
    }

    public TokenEntity getToken() {
        return token;
    }

    public void setToken(TokenEntity token) {
        this.token = token;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
