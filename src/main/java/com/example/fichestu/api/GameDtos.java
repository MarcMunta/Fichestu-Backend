package com.example.fichestu.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
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
        private String token;

        private Integer tokenId;

        @Min(1)
        private Integer quantity = 1;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public Integer getTokenId() {
            return tokenId;
        }

        public void setTokenId(Integer tokenId) {
            this.tokenId = tokenId;
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

    public record MarketSnapshotResponse(
        String message,
        Boolean success,
        BigDecimal cashBalance,
        BigDecimal totalBalance,
        Integer rewardedCooldownSec,
        Integer rewardedAdsClaimed,
        List<TokenDto> tokens,
        List<TransactionDto> transactions
    ) {
    }

    public record TransactionDto(
        Integer transactionId,
        String type,
        BigDecimal amountFiat,
        String description,
        Instant createdAt
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
        Long selectionDeadlineEpochMs,
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

        private Integer tokenId;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getCardType() {
            return action;
        }

        public void setCardType(String cardType) {
            this.action = cardType;
        }

        public String getSelectedToken() {
            return selectedToken;
        }

        public void setSelectedToken(String selectedToken) {
            this.selectedToken = selectedToken;
        }

        public Integer getTokenId() {
            return tokenId;
        }

        public void setTokenId(Integer tokenId) {
            this.tokenId = tokenId;
        }
    }

    public static class WinnerImpactRequest {
        @NotBlank
        private String token;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
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
