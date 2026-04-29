package com.example.fichestu.realtime;

public record MatchUpdateEvent(String type, Integer matchId) {

    public static MatchUpdateEvent updated(Integer matchId) {
        return new MatchUpdateEvent("MATCH_UPDATED", matchId);
    }
}
