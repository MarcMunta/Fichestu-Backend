package com.example.fichestu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserWalletId implements Serializable {

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "token_id")
    private Integer tokenId;

    public UserWalletId() {
    }

    public UserWalletId(Integer userId, Integer tokenId) {
        this.userId = userId;
        this.tokenId = tokenId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getTokenId() {
        return tokenId;
    }

    public void setTokenId(Integer tokenId) {
        this.tokenId = tokenId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserWalletId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
            && Objects.equals(tokenId, that.tokenId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tokenId);
    }
}
