package com.example.fichestu;

import com.example.fichestu.persistence.entity.GameSessionEntity;
import com.example.fichestu.persistence.entity.MatchParticipantEntity;
import com.example.fichestu.persistence.entity.TokenPriceHistoryEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.service.MarketMaintenanceService;
import com.example.fichestu.support.RandomProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(GameIntegrationTests.RandomTestConfig.class)
class GameIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private MarketMaintenanceService marketMaintenanceService;

    @Autowired
    private TestRandomProvider testRandomProvider;

    @BeforeEach
    void resetRandomProvider() {
        testRandomProvider.reset();
    }

    @Test
    void marketSnapshotRetrievalWorksForAuthenticatedUser() throws Exception {
        createDefaultTokens();
        markDailyResetExecuted(currentBusinessDate());
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("200.00"));

        mockMvc.perform(get("/api/game/market")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tokens.length()").value(5))
            .andExpect(jsonPath("$.cashBalance").value(200.00))
            .andExpect(jsonPath("$.portfolioValue").value(1850.00))
            .andExpect(jsonPath("$.totalBalance").value(2050.00));
    }

    @Test
    void buyCreatesWalletAndRejectsInsufficientFiat() throws Exception {
        createDefaultTokens();
        markDailyResetExecuted(currentBusinessDate());
        var richUser = createUser("rich", "rich@test.com", "secret123", "USER", new BigDecimal("200.00"));
        var poorUser = createUser("poor", "poor@test.com", "secret123", "USER", new BigDecimal("5.00"));

        mockMvc.perform(post("/api/game/market/buy")
                .header("Authorization", bearerFor(richUser))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", "FRO", "quantity", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(150.00))
            .andExpect(jsonPath("$.totalBalance").value(2050.00));

        assertThat(walletFor(richUser, "Ficha Roja").getQuantity()).isEqualByComparingTo(new BigDecimal("11.0000"));
        assertThat(walletFor(richUser, "Ficha Gris").getQuantity()).isEqualByComparingTo(new BigDecimal("150.0000"));
        assertThat(transactionLogRepository.findTop20ByUserUserIdOrderByCreatedAtDesc(richUser.getUserId()))
            .anyMatch(log -> "EXCHANGE_BUY".equals(log.getType()));

        mockMvc.perform(post("/api/game/market/buy")
                .header("Authorization", bearerFor(poorUser))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "FRO", "quantity", 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Saldo insuficiente para comprar"));
    }

    @Test
    void totalBalanceTracksOwnedTokenMarketValueWithoutLiquidatingHoldings() throws Exception {
        createDefaultTokens();
        markDailyResetExecuted(currentBusinessDate());
        var user = createUser("portfolio", "portfolio@test.com", "secret123", "USER", new BigDecimal("200.00"));

        mockMvc.perform(post("/api/game/market/buy")
                .header("Authorization", bearerFor(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", "FRO", "quantity", 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(100.00))
            .andExpect(jsonPath("$.portfolioValue").value(1950.00))
            .andExpect(jsonPath("$.totalBalance").value(2050.00))
            .andExpect(jsonPath("$.tokens[0].holdingValue").value(600.00))
            .andExpect(jsonPath("$.tokens[0].portfolioWeightPercent").value(30.77));

        var red = findTokenByName("Ficha Roja");
        red.setCurrentPrice(new BigDecimal("25.00"));
        red.setLastUpdate(Instant.now());
        tokenRepository.save(red);

        TokenPriceHistoryEntity history = new TokenPriceHistoryEntity();
        history.setToken(red);
        history.setPrice(new BigDecimal("25.00"));
        tokenPriceHistoryRepository.save(history);

        mockMvc.perform(get("/api/game/market")
            .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(100.00))
            .andExpect(jsonPath("$.portfolioValue").value(1650.00))
            .andExpect(jsonPath("$.totalBalance").value(1750.00))
            .andExpect(jsonPath("$.tokens[0].holdingValue").value(300.00))
            .andExpect(jsonPath("$.tokens[0].holdingChangeValue").value(-300.00));
    }

    @Test
    void sellWorksAndRejectsInsufficientHoldings() throws Exception {
        createDefaultTokens();
        markDailyResetExecuted(currentBusinessDate());
        var user = createUser("seller", "seller@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(post("/api/game/market/buy")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "FAZ", "quantity", 1))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/market/sell")
            .header("Authorization", bearerFor(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("token", "FAZ", "quantity", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(100.00));

        assertThat(walletFor(user, "Ficha Gris").getQuantity()).isEqualByComparingTo(new BigDecimal("100.0000"));

        mockMvc.perform(post("/api/game/market/sell")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "FAZ", "quantity", 11))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("No tienes suficientes fichas para vender"));
    }

    @Test
    void marketOnlyUpdatesPricesOnDailyResetAndResetIsIdempotent() {
        createDefaultTokens();
        markDailyResetExecuted(currentBusinessDate());
        var user = createUser("holder", "holder@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvcPerformBuy(user, "FVD", 2);

        BigDecimal priceBeforeTick = findTokenByName("Ficha Roja").getCurrentPrice();
        boolean minuteTickChangedPrices = marketMaintenanceService.advanceMarketIfStale();
        assertThat(minuteTickChangedPrices).isFalse();
        assertThat(findTokenByName("Ficha Roja").getCurrentPrice()).isEqualByComparingTo(priceBeforeTick);

        BigDecimal balanceBeforeReset = userRepository.findById(user.getUserId()).orElseThrow().getFiatBalance();
        LocalDate nextBusinessDate = currentBusinessDate().plusDays(1);
        boolean firstRun = marketMaintenanceService.runDailyResetForBusinessDate(nextBusinessDate);
        boolean secondRun = marketMaintenanceService.runDailyResetForBusinessDate(nextBusinessDate);

        assertThat(firstRun).isTrue();
        assertThat(secondRun).isFalse();

        var refreshedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(refreshedUser.getFiatBalance()).isEqualByComparingTo(balanceBeforeReset);
        assertThat(findTokenByName("Ficha Roja").getCurrentPrice()).isNotEqualByComparingTo(priceBeforeTick);
        assertThat(findTokenByName("Ficha Gris").getCurrentPrice()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(tokenPriceHistoryRepository.countByTokenTokenId(findTokenByName("Ficha Roja").getTokenId())).isGreaterThan(1);
        assertThat(walletFor(user, "Ficha Verde").getQuantity())
            .isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void joiningSessionUpToPlayerLimitRejectsEleventhPlayer() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(11, new BigDecimal("100.00"));

        String createBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(createBody).get("matchId").asInt();

        for (int i = 1; i < 10; i++) {
            mockMvc.perform(post("/api/game/matches/{matchId}/join", matchId)
                    .header("Authorization", bearerFor(users.get(i))))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/game/matches/{matchId}/join", matchId)
                .header("Authorization", bearerFor(users.get(10))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La sala ya está llena"));
    }

    @Test
    void enterBallRoomReusesOpenMatchmakingRoomAndResetsDeadlineForRealJoin() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(2, new BigDecimal("100.00"));

        String firstBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode firstJson = objectMapper.readTree(firstBody);
        int matchId = firstJson.get("matchId").asInt();
        long firstDeadline = firstJson.get("ballRoom").get("selectionDeadlineEpochMs").asLong();

        String secondBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(1))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        com.fasterxml.jackson.databind.JsonNode secondJson = objectMapper.readTree(secondBody);
        assertThat(secondJson.get("matchId").asInt()).isEqualTo(matchId);
        assertThat(secondJson.get("ballRoom").get("selectionDeadlineEpochMs").asLong()).isGreaterThanOrEqualTo(firstDeadline);
        assertThat(matchParticipantRepository.countByIdMatchId(matchId)).isEqualTo(2);
    }

    @Test
    void enterBallRoomWithoutStumDoesNotSpendPortfolioTokens() throws Exception {
        createDefaultTokens();
        UserEntity user = createUser("nogrey", "nogrey@test.com", "secret123", "USER", new BigDecimal("100.00"));
        UserWalletEntity greyWallet = walletFor(user, "Ficha Gris");
        greyWallet.setQuantity(new BigDecimal("0.0000"));
        userWalletRepository.save(greyWallet);
        BigDecimal greenBefore = walletFor(user, "Ficha Verde").getQuantity();

        mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(user))
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("paymentTokenIds", List.of()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Saldo insuficiente para entrar en la sala"));

        assertThat(walletFor(user, "Ficha Gris").getQuantity()).isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(walletFor(user, "Ficha Verde").getQuantity()).isEqualByComparingTo(greenBefore);
    }

    @Test
    void leavingMatchmakingThroughAbandonDoesNotRefundEntry() throws Exception {
        createDefaultTokens();
        UserEntity user = createUser("leaver", "leaver@test.com", "secret123", "USER", new BigDecimal("100.00"));

        String enterBody = mockMvc.perform(post("/api/game/ball-room/enter")
            .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(90.00))
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(enterBody).get("matchId").asInt();

        mockMvc.perform(post("/api/game/matches/{matchId}/abandon", matchId)
            .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(90.00));

        assertThat(walletFor(user, "Ficha Gris").getQuantity()).isEqualByComparingTo(new BigDecimal("90.0000"));
        assertThat(matchParticipantRepository.existsByIdMatchIdAndIdUserId(matchId, user.getUserId())).isFalse();
        assertThat(gameSessionRepository.findById(matchId)).isEmpty();
    }

    @Test
    void logoutDetachesUserFromActiveMatchWithoutRefund() throws Exception {
        createDefaultTokens();
        UserEntity user = createUser("logoutmatch", "logoutmatch@test.com", "secret123", "USER", new BigDecimal("100.00"));
        String bearer = bearerFor(user);

        String enterBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(90.00))
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(enterBody).get("matchId").asInt();

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", bearer))
            .andExpect(status().isOk());

        assertThat(walletFor(user, "Ficha Gris").getQuantity()).isEqualByComparingTo(new BigDecimal("90.0000"));
        assertThat(matchParticipantRepository.existsByIdMatchIdAndIdUserId(matchId, user.getUserId())).isFalse();
        assertThat(gameSessionRepository.findById(matchId)).isEmpty();
    }

    @Test
    void reenteringWhileInMatchmakingDoesNotChargeTwice() throws Exception {
        createDefaultTokens();
        UserEntity user = createUser("reenter", "reenter@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(post("/api/game/ball-room/enter")
            .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(90.00));

        mockMvc.perform(post("/api/game/ball-room/enter")
            .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(90.00));

        UserEntity refreshed = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(refreshed.getFiatBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void cancelAfterMatchmakingAdvancesStillRefundsEntry() throws Exception {
        createDefaultTokens();
        UserEntity user = createUser("latecancel", "latecancel@test.com", "secret123", "USER", new BigDecimal("100.00"));

        String enterBody = mockMvc.perform(post("/api/game/ball-room/enter")
            .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(90.00))
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(enterBody).get("matchId").asInt();

        GameSessionEntity session = gameSessionRepository.findById(matchId).orElseThrow();
        session.setMatchmakingDeadline(Instant.now().minusSeconds(1));
        gameSessionRepository.save(session);

        mockMvc.perform(get("/api/game/match/state")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ballRoom.phase").value("PICKING"));

        mockMvc.perform(post("/api/game/matches/{matchId}/matchmaking/cancel", matchId)
            .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(100.00));

        UserEntity refreshed = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(refreshed.getFiatBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(transactionLogRepository.existsByUserUserIdAndTypeAndDescription(
            user.getUserId(),
            "BALL_ENTRY_REFUND",
            "Devolucion entrada sala #" + matchId
        )).isTrue();
    }

    @Test
    void ballPickingUsesSharedServerDeadlineAndAssignsMissingPlayersOnTimeout() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(2, new BigDecimal("100.00"));

        String createBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(createBody).get("matchId").asInt();

        mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(1))))
            .andExpect(status().isOk());

        GameSessionEntity session = gameSessionRepository.findById(matchId).orElseThrow();
        session.setMatchmakingDeadline(Instant.now().minusSeconds(1));
        gameSessionRepository.save(session);

        String pickingBody = mockMvc.perform(get("/api/game/match/state")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ballRoom.phase").value("PICKING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        long selectionDeadline = objectMapper.readTree(pickingBody).get("ballRoom").get("selectionDeadlineEpochMs").asLong();
        assertThat(selectionDeadline).isGreaterThan(Instant.now().toEpochMilli());

        mockMvc.perform(post("/api/game/matches/{matchId}/pick-ball", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ballId", 9))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ballRoom.balls[8].pickedBy").value(String.valueOf(users.get(0).getUserId())));

        session = gameSessionRepository.findById(matchId).orElseThrow();
        session.setMatchmakingDeadline(Instant.now().minusSeconds(1));
        gameSessionRepository.save(session);

        mockMvc.perform(get("/api/game/match/state")
                .header("Authorization", bearerFor(users.get(1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ballRoom.phase").value("PICKING"))
            .andExpect(jsonPath("$.ballRoom.canRevealBattle").value(true));

        MatchParticipantEntity secondPlayer = matchParticipantRepository.findByIdMatchId(matchId)
            .stream()
            .filter(participant -> participant.getUser().getUserId().equals(users.get(1).getUserId()))
            .findFirst()
            .orElseThrow();
        assertThat(secondPlayer.getSelectedBallNumber()).isNotNull();
    }

    @Test
    void leavingBeforeBattleStartsDoesNotRefundEntry() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(10, new BigDecimal("100.00"));

        String createBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(createBody).get("matchId").asInt();

        for (int i = 1; i < 10; i++) {
            mockMvc.perform(post("/api/game/matches/{matchId}/join", matchId)
                    .header("Authorization", bearerFor(users.get(i))))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/game/matches/{matchId}/abandon", matchId)
            .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cashBalance").value(90.00));

        assertThat(walletFor(users.get(0), "Ficha Gris").getQuantity()).isEqualByComparingTo(new BigDecimal("90.0000"));
        assertThat(matchParticipantRepository.existsByIdMatchIdAndIdUserId(matchId, users.get(0).getUserId())).isFalse();
        assertThat(matchParticipantRepository.countByIdMatchId(matchId)).isEqualTo(10);
    }

    @Test
    void battleActionIsPreparedOnceUntilRoundResolves() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(2, new BigDecimal("100.00"));

        String createBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(createBody).get("matchId").asInt();

        mockMvc.perform(post("/api/game/matches/{matchId}/join", matchId)
                .header("Authorization", bearerFor(users.get(1))))
            .andExpect(status().isOk());

        GameSessionEntity session = gameSessionRepository.findById(matchId).orElseThrow();
        session.setStatus("IN_PROGRESS");
        session.setBattleRoundDeadline(Instant.now().plusSeconds(30));
        gameSessionRepository.save(session);

        mockMvc.perform(post("/api/game/matches/{matchId}/battle/round", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "ATTACK", "cardPower", 3))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.battle.userActionSubmitted").value(true));

        mockMvc.perform(post("/api/game/matches/{matchId}/battle/round", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "SHIELD", "cardPower", 1))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Ya tienes una carta preparada para esta ronda."));

        var card = matchCardRepository.findByMatchMatchIdAndRoundNumberAndOwnerUserId(matchId, 1, users.get(0).getUserId())
            .stream()
            .findFirst()
            .orElseThrow();
        assertThat(card.getCardType()).isEqualTo("ATTACK");
        assertThat(card.getCardValue()).isEqualTo(3);
    }

    @Test
    void duplicateBallSelectionIsRejectedAndHappyPathReachesMarketImpactExactlyOnce() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(10, new BigDecimal("100.00"));

        String createBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(createBody).get("matchId").asInt();

        for (int i = 1; i < 10; i++) {
            mockMvc.perform(post("/api/game/matches/{matchId}/join", matchId)
                    .header("Authorization", bearerFor(users.get(i))))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/game/matches/{matchId}/pick-ball", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ballId", 1))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/game/matches/{matchId}/pick-ball", matchId)
                .header("Authorization", bearerFor(users.get(1)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ballId", 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Esa bola ya fue tomada"));

        for (int i = 1; i < 10; i++) {
            mockMvc.perform(post("/api/game/matches/{matchId}/pick-ball", matchId)
                    .header("Authorization", bearerFor(users.get(i)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("ballId", i + 1))))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/game/matches/{matchId}/reveal", matchId)
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.battle.phase").value("READY"));

        GameSessionEntity session = gameSessionRepository.findById(matchId).orElseThrow();
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(matchId);
        UserEntity botTarget = userRepository.findById(users.get(1).getUserId()).orElseThrow();
        botTarget.setRole("BOT");
        userRepository.save(botTarget);
        for (int i = 0; i < participants.size(); i++) {
            MatchParticipantEntity participant = participants.get(i);
            if (participant.getUser().getUserId().equals(users.get(0).getUserId())) {
                participant.setCurrentHp(50);
                participant.setAlive(true);
                participant.setMultiplierWon(new BigDecimal("4.00"));
            } else if (i == 1) {
                participant.setCurrentHp(1);
                participant.setAlive(true);
                participant.setMultiplierWon(new BigDecimal("1.10"));
            } else {
                participant.setCurrentHp(0);
                participant.setAlive(false);
            }
        }
        matchParticipantRepository.saveAll(participants);
        session.setStatus("REVEALED");
        gameSessionRepository.save(session);

        BigDecimal oldPrice = findTokenByName("Ficha Roja").getCurrentPrice();

        mockMvc.perform(post("/api/game/matches/{matchId}/battle/round", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "action", "ATTACK",
                    "selectedToken", "FRO"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.battle.phase").value("FINISHED"))
            .andExpect(jsonPath("$.battle.winnerId").value(String.valueOf(users.get(0).getUserId())));

        GameSessionEntity finishedSession = gameSessionRepository.findById(matchId).orElseThrow();
        assertThat(finishedSession.getImpactApplied()).isTrue();
        assertThat(finishedSession.getWinnerTokenAlias()).isEqualTo("FRO");
        assertThat(findTokenByName("Ficha Roja").getCurrentPrice())
            .isEqualByComparingTo(oldPrice.multiply(new BigDecimal("3.00")));

        mockMvc.perform(post("/api/game/matches/{matchId}/winner-impact", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "FRO"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("El impacto ya fue aplicado"));
    }

    @Test
    void lateBallPickKeepsUserChoiceInsteadOfAutoAssigningFirst() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(10, new BigDecimal("100.00"));

        String createBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(createBody).get("matchId").asInt();

        for (int i = 1; i < 10; i++) {
            mockMvc.perform(post("/api/game/matches/{matchId}/join", matchId)
                    .header("Authorization", bearerFor(users.get(i))))
                .andExpect(status().isOk());
        }

        GameSessionEntity session = gameSessionRepository.findById(matchId).orElseThrow();
        session.setMatchmakingDeadline(Instant.now().minusSeconds(1));
        gameSessionRepository.save(session);

        mockMvc.perform(post("/api/game/matches/{matchId}/pick-ball", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ballId", 11))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ballRoom.balls[10].pickedBy").value(String.valueOf(users.get(0).getUserId())));

        MatchParticipantEntity participant = matchParticipantRepository.findByIdMatchId(matchId).stream()
            .filter(value -> value.getUser().getUserId().equals(users.get(0).getUserId()))
            .findFirst()
            .orElseThrow();
        assertThat(participant.getSelectedBallNumber()).isEqualTo(11);
    }

    @Test
    void battleClosesWhenOnlyBotsRemainAlive() throws Exception {
        createDefaultTokens();
        List<UserEntity> users = createPlayers(10, new BigDecimal("100.00"));

        String createBody = mockMvc.perform(post("/api/game/ball-room/enter")
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        int matchId = objectMapper.readTree(createBody).get("matchId").asInt();

        for (int i = 1; i < 10; i++) {
            mockMvc.perform(post("/api/game/matches/{matchId}/join", matchId)
                    .header("Authorization", bearerFor(users.get(i))))
                .andExpect(status().isOk());
        }

        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/game/matches/{matchId}/pick-ball", matchId)
                    .header("Authorization", bearerFor(users.get(i)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("ballId", i + 1))))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/game/matches/{matchId}/reveal", matchId)
                .header("Authorization", bearerFor(users.get(0))))
            .andExpect(status().isOk());

        GameSessionEntity session = gameSessionRepository.findById(matchId).orElseThrow();
        for (int i = 1; i < 10; i++) {
            users.get(i).setRole("BOT");
            userRepository.save(users.get(i));
        }
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByIdMatchId(matchId);
        for (MatchParticipantEntity participant : participants) {
            if (participant.getUser().getUserId().equals(users.get(0).getUserId())) {
                participant.setCurrentHp(1);
            } else {
                participant.setCurrentHp(50);
            }
            participant.setAlive(true);
        }
        matchParticipantRepository.saveAll(participants);
        session.setStatus("REVEALED");
        gameSessionRepository.save(session);

        mockMvc.perform(post("/api/game/matches/{matchId}/battle/round", matchId)
                .header("Authorization", bearerFor(users.get(0)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("action", "ATTACK"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.battle.phase").value("FINISHED"));

        GameSessionEntity finishedSession = gameSessionRepository.findById(matchId).orElseThrow();
        assertThat(finishedSession.getStatus()).isEqualTo("FINISHED");
        assertThat(finishedSession.getWinner()).isNull();
    }

    private void mockMvcPerformBuy(UserEntity user, String token, int quantity) {
        try {
            mockMvc.perform(post("/api/game/market/buy")
                    .header("Authorization", bearerFor(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("token", token, "quantity", quantity))))
                .andExpect(status().isOk());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<UserEntity> createPlayers(int count, BigDecimal startingBalance) {
        List<UserEntity> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(createUser("player" + i, "player" + i + "@test.com", "secret123", "USER", startingBalance));
        }
        return users;
    }

    private UserWalletEntity walletFor(UserEntity user, String tokenName) {
        Integer tokenId = findTokenByName(tokenName).getTokenId();
        return userWalletRepository.findByIdUserId(user.getUserId()).stream()
            .filter(wallet -> wallet.getId() != null && tokenId.equals(wallet.getId().getTokenId()))
            .findFirst()
            .orElseThrow();
    }

    @TestConfiguration
    static class RandomTestConfig {
        @Bean
        @Primary
        TestRandomProvider testRandomProvider() {
            return new TestRandomProvider();
        }
    }

    static class TestRandomProvider extends RandomProvider {
        void reset() {
        }

        @Override
        public double nextDouble() {
            return 0.1;
        }

        @Override
        public int nextInt(int boundExclusive) {
            return 0;
        }
    }
}
