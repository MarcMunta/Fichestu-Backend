package com.example.fichestu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "badges")
public class BadgeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "badge_id")
    private Integer badgeId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    public Integer getBadgeId() {
        return badgeId;
    }

    public void setBadgeId(Integer badgeId) {
        this.badgeId = badgeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
