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
            .andExpect(jsonPath("$.tokens.length()").value(4))
            .andExpect(jsonPath("$.cashBalance").value(200.00));
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
            .andExpect(jsonPath("$.cashBalance").value(150.00));

        UserWalletEntity wallet = userWalletRepository.findByIdUserId(richUser.getUserId()).get(0);
        assertThat(wallet.getQuantity()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(transactionLogRepository.findTop20ByUserUserIdOrderByCreatedAtDesc(richUser.getUserId()))
            .anyMatch(log -> "BUY".equals(log.getType()));

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
            .andExpect(jsonPath("$.portfolioValue").value(100.00))
            .andExpect(jsonPath("$.totalBalance").value(200.00))
            .andExpect(jsonPath("$.tokens[0].holdingValue").value(100.00))
            .andExpect(jsonPath("$.tokens[0].portfolioWeightPercent").value(100.00));

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
            .andExpect(jsonPath("$.portfolioValue").value(50.00))
            .andExpect(jsonPath("$.totalBalance").value(150.00))
            .andExpect(jsonPath("$.tokens[0].holdingValue").value(50.00))
            .andExpect(jsonPath("$.tokens[0].holdingChangeValue").value(-50.00));
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

        mockMvc.perform(post("/api/game/market/sell")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", "FAZ", "quantity", 1))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("No tienes suficientes fichas para vender"));
    }

    @Test
    void marketUpdatesPersistPriceHistoryAndDailyResetIsIdempotent() {
        createDefaultTokens();
        markDailyResetExecuted(currentBusinessDate());
        var user = createUser("holder", "holder@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvcPerformBuy(user, "FVD", 2);

        tokenRepository.findAll().forEach(token -> {
            token.setLastUpdate(Instant.EPOCH);
            tokenRepository.save(token);
        });
        marketMaintenanceService.advanceMarketIfStale();
        assertThat(tokenPriceHistoryRepository.countByTokenTokenId(findTokenByName("Ficha Roja").getTokenId())).isGreaterThan(1);

        BigDecimal balanceBeforeReset = userRepository.findById(user.getUserId()).orElseThrow().getFiatBalance();
        LocalDate nextBusinessDate = currentBusinessDate().plusDays(1);
        boolean firstRun = marketMaintenanceService.runDailyResetForBusinessDate(nextBusinessDate);
        boolean secondRun = marketMaintenanceService.runDailyResetForBusinessDate(nextBusinessDate);

        assertThat(firstRun).isTrue();
        assertThat(secondRun).isFalse();

        var refreshedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(refreshedUser.getFiatBalance()).isGreaterThan(balanceBeforeReset);
        assertThat(userWalletRepository.findByIdUserId(user.getUserId()).get(0).getQuantity())
            .isEqualByComparingTo(new BigDecimal("0.0000"));
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
