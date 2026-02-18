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
import java.time.Instant;

@Entity
@Table(name = "user_badges")
public class UserBadgeEntity {

    @EmbeddedId
    private UserBadgeId id = new UserBadgeId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("badgeId")
    @JoinColumn(name = "badge_id", nullable = false)
    private BadgeEntity badge;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private Instant awardedAt;

    @PrePersist
    public void prePersist() {
        if (awardedAt == null) {
            awardedAt = Instant.now();
        }
    }

    public UserBadgeId getId() {
        return id;
    }

    public void setId(UserBadgeId id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public BadgeEntity getBadge() {
        return badge;
    }

    public void setBadge(BadgeEntity badge) {
        this.badge = badge;
    }

    public Instant getAwardedAt() {
        return awardedAt;
    }

    public void setAwardedAt(Instant awardedAt) {
        this.awardedAt = awardedAt;
    }
}
