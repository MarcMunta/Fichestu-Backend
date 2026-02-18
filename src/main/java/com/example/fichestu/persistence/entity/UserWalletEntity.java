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
@Table(name = "user_wallets")
public class UserWalletEntity {

    @EmbeddedId
    private UserWalletId id = new UserWalletId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("tokenId")
    @JoinColumn(name = "token_id", nullable = false)
    private TokenEntity token;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity = BigDecimal.ZERO;

    @PrePersist
    public void prePersist() {
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
    }

    public UserWalletId getId() {
        return id;
    }

    public void setId(UserWalletId id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public TokenEntity getToken() {
        return token;
    }

    public void setToken(TokenEntity token) {
        this.token = token;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }
}
