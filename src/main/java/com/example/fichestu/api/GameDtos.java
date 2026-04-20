package com.example.fichestu.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public final class GameDtos {

    private GameDtos() {
    }

    public record BootstrapResponse(
        String message,
        Boolean success,
        Integer userId,
        String playerName,
        BigDecimal cashBalance,
        Integer rewardedCooldownSec,
        List<TokenDto> tokens,
        List<BadgeDto> badges,
        ProfileStatsDto stats
    ) {
    }

    public record TokenDto(
        Integer tokenId,
        String name,
        String ticker,
        String colorCode,
        BigDecimal currentPrice,
        BigDecimal previousPrice,
        BigDecimal holdings,
        List<BigDecimal> history
    ) {
    }

    public record BadgeDto(
        Integer badgeId,
        String title,
        String description,
        Boolean unlocked
    ) {
    }

    public record ProfileStatsDto(
        Integer ballRoomsPlayed,
        Integer battlesPlayed,
        Integer battlesWon,
        Double bestMultiplier,
        Double averageMultiplier,
        Integer rewardedAdsClaimed
    ) {
    }

    public static class TradeRequest {
        @NotBlank
        private String token;

        @Min(1)
        private Integer quantity = 1;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
    }

    public record WalletResponse(
        String message,
        Boolean success,
        BigDecimal cashBalance,
        BigDecimal totalBalance,
        List<TokenDto> tokens
    ) {
    }

    public record EnterBallRoomResponse(
        String message,
        Boolean success,
        Integer matchId,
        BigDecimal cashBalance,
        BallRoomDto ballRoom
    ) {
    }

    public record BallRoomDto(
        String phase,
        String statusMessage,
        Boolean canRevealBattle,
        List<BallPlayerDto> players,
        List<BallOptionDto> balls
    ) {
    }

    public record BallPlayerDto(
        String id,
        String nickname,
        Boolean isUser,
        Integer selectedBallId,
        Double multiplier
    ) {
    }

    public record BallOptionDto(
        Integer id,
        Double multiplier,
        String pickedBy
    ) {
    }

    public static class PickBallRequest {
        @Min(1)
        @Max(50)
        private Integer ballId;

        public Integer getBallId() {
            return ballId;
        }

        public void setBallId(Integer ballId) {
            this.ballId = ballId;
        }
    }

    public record MatchStateResponse(
        String message,
        Boolean success,
        Integer matchId,
        BallRoomDto ballRoom,
        BattleDto battle
    ) {
    }

    public static class BattleActionRequest {
        @NotBlank
        private String action;

        private String selectedToken;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getSelectedToken() {
            return selectedToken;
        }

        public void setSelectedToken(String selectedToken) {
            this.selectedToken = selectedToken;
        }
    }

    public record BattleDto(
        String phase,
        Integer round,
        String winnerId,
        String winnerName,
        Double winningMultiplier,
        String selectedAction,
        Boolean interstitialAvailable,
        List<String> log,
        List<BattlePlayerDto> players
    ) {
    }

    public record BattlePlayerDto(
        String id,
        String nickname,
        Boolean isUser,
        Integer hp,
        Double multiplier,
        Boolean isAlive
    ) {
    }

    public record CooldownResponse(
        String message,
        Boolean success,
        BigDecimal cashBalance,
        Integer rewardedCooldownSec,
        Integer rewardedAdsClaimed
    ) {
    }

    public record GenericMessageResponse(
        String message,
        Boolean success
    ) {
    }
}
