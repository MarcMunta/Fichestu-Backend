package com.example.fichestu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserBadgeId implements Serializable {

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "badge_id")
    private Integer badgeId;

    public UserBadgeId() {
    }

    public UserBadgeId(Integer userId, Integer badgeId) {
        this.userId = userId;
        this.badgeId = badgeId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getBadgeId() {
        return badgeId;
    }

    public void setBadgeId(Integer badgeId) {
        this.badgeId = badgeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserBadgeId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId)
            && Objects.equals(badgeId, that.badgeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, badgeId);
    }
}
