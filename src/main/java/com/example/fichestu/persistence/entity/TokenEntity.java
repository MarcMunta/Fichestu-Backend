package com.example.fichestu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "tokens")
public class TokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Integer tokenId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "color_code", length = 7)
    private String colorCode;

    @Column(name = "current_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentPrice = new BigDecimal("10.00");

    @Column(name = "last_update", nullable = false)
    private Instant lastUpdate;

    @PrePersist
    public void prePersist() {
        if (currentPrice == null) {
            currentPrice = new BigDecimal("10.00");
        }
        if (lastUpdate == null) {
            lastUpdate = Instant.now();
        }
    }

    public Integer getTokenId() {
        return tokenId;
    }

    public void setTokenId(Integer tokenId) {
        this.tokenId = tokenId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColorCode() {
        return colorCode;
    }

    public void setColorCode(String colorCode) {
        this.colorCode = colorCode;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
