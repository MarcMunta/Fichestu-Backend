package com.example.fichestu.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class MinigameDtos {

    private MinigameDtos() {
    }

    public static class StartMinigameRequest {
        @NotBlank
        private String gameType;

        private String paymentType = "AUTO";

        public String getGameType() {
            return gameType;
        }

        public void setGameType(String gameType) {
            this.gameType = gameType;
        }

        public String getPaymentType() {
            return paymentType;
        }

        public void setPaymentType(String paymentType) {
            this.paymentType = paymentType;
        }
    }

    public static class FinishMinigameRequest {
        @Min(0)
        private Integer score = 0;

        private BigDecimal rewardStum = BigDecimal.ZERO;

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public BigDecimal getRewardStum() {
            return rewardStum;
        }

        public void setRewardStum(BigDecimal rewardStum) {
            this.rewardStum = rewardStum;
        }
    }

    public record MinigameAccessListResponse(
        String message,
        Boolean success,
        BigDecimal cashBalance,
        List<MinigameAccessResponse> games
    ) {
    }

    public record MinigameAccessResponse(
        String gameType,
        Boolean freeAvailable,
        Integer freeCooldownSec,
        Instant nextFreeAt,
        BigDecimal paidEntryCost,
        BigDecimal maxReward,
        BigDecimal cashBalance
    ) {
    }

    public record MinigameStartResponse(
        String message,
        Boolean success,
        Integer attemptId,
        String gameType,
        String paymentType,
        BigDecimal entryCost,
        BigDecimal cashBalance,
        Boolean freeAvailable,
        Integer freeCooldownSec,
        Instant nextFreeAt
    ) {
    }

    public record MinigameFinishResponse(
        String message,
        Boolean success,
        Integer attemptId,
        String gameType,
        Integer score,
        BigDecimal rewardStum,
        BigDecimal cashBalance,
        Boolean freeAvailable,
        Integer freeCooldownSec,
        Instant nextFreeAt
    ) {
    }
}
