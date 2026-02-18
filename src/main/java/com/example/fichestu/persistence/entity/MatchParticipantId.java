package com.example.fichestu.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class MatchParticipantId implements Serializable {

    @Column(name = "match_id")
    private Integer matchId;

    @Column(name = "user_id")
    private Integer userId;

    public MatchParticipantId() {
    }

    public MatchParticipantId(Integer matchId, Integer userId) {
        this.matchId = matchId;
        this.userId = userId;
    }

    public Integer getMatchId() {
        return matchId;
    }

    public void setMatchId(Integer matchId) {
        this.matchId = matchId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MatchParticipantId that)) {
            return false;
        }
        return Objects.equals(matchId, that.matchId)
            && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, userId);
    }
}
