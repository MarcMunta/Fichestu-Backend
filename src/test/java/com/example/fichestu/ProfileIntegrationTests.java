package com.example.fichestu;

import com.example.fichestu.persistence.entity.GameSessionEntity;
import com.example.fichestu.persistence.entity.GameSessionEventEntity;
import com.example.fichestu.persistence.entity.MatchParticipantEntity;
import com.example.fichestu.persistence.entity.MatchParticipantId;
import com.example.fichestu.persistence.entity.TransactionLogEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTests extends IntegrationTestSupport {

    @Test
    void authenticatedProfileFetchWorks() throws Exception {
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));
        user.setProfilePicUrl("https://example.com/avatar.png");
        user.setProfileCardBackground("hero:casino");
        user.setProfilePageBackground("screen:default");
        userRepository.save(user);

        mockMvc.perform(get("/api/profile")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@test.com"))
            .andExpect(jsonPath("$.profilePicUrl").value("https://example.com/avatar.png"))
            .andExpect(jsonPath("$.hasPassword").value(true))
            .andExpect(jsonPath("$.profileCardBackground").value("hero:casino"))
            .andExpect(jsonPath("$.profilePageBackground").value("screen:default"));
    }

    @Test
    void profileMarksGoogleOnlyUserWithoutPassword() throws Exception {
        var user = createUser("google-user", "google@test.com", "secret123", "USER", new BigDecimal("100.00"));
        user.setPasswordHash(null);
        userRepository.save(user);

        mockMvc.perform(get("/api/profile")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasPassword").value(false));
    }

    @Test
    void unauthenticatedProfileFetchIsRejected() throws Exception {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Sesión inválida"));
    }

    @Test
    void usernameUpdateEnforcesUniqueness() throws Exception {
        createUser("ocupado", "ocupado@test.com", "secret123", "USER", new BigDecimal("100.00"));
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(put("/api/profile")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "ocupado",
                    "email", "alice@test.com",
                    "profilePicUrl", "https://example.com/new.png"
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("El username ya está en uso"));
    }

    @Test
    void profileUpdateAllowsChangingOwnUsernameEmailAndAvatar() throws Exception {
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(put("/api/profile")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "alice-renamed",
                    "email", "alice-renamed@test.com",
                    "profilePicUrl", "https://example.com/new.png"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Perfil actualizado"))
            .andExpect(jsonPath("$.username").value("alice-renamed"))
            .andExpect(jsonPath("$.email").value("alice-renamed@test.com"))
            .andExpect(jsonPath("$.profilePicUrl").value("https://example.com/new.png"));
    }

    @Test
    void profileStylePersistsCardAndPageBackgrounds() throws Exception {
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(put("/api/profile/style")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "profileCardBackground", "hero:mystic-purple",
                    "profilePageBackground", "screen:ocean"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCardBackground").value("hero:mystic-purple"))
            .andExpect(jsonPath("$.profilePageBackground").value("screen:ocean"));

        mockMvc.perform(get("/api/profile")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.profileCardBackground").value("hero:mystic-purple"))
            .andExpect(jsonPath("$.profilePageBackground").value("screen:ocean"));
    }

    @Test
    void passwordChangeSupportsSuccessAndRejectsWrongCurrentPassword() throws Exception {
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(post("/api/profile/change-password")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "currentPassword", "bad-current",
                    "newPassword", "new-secret",
                    "confirmPassword", "new-secret"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));

        mockMvc.perform(post("/api/profile/change-password")
                .header("Authorization", bearerFor(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "currentPassword", "secret123",
                    "newPassword", "new-secret",
                    "confirmPassword", "new-secret"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Contrasena actualizada"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "alice@test.com",
                    "password", "new-secret"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void badgePayloadWorksForPlayersWithAndWithoutBadges() throws Exception {
        var userWithoutBadges = createUser("plain", "plain@test.com", "secret123", "USER", new BigDecimal("100.00"));
        var userWithBadges = createUser("champ", "champ@test.com", "secret123", "USER", new BigDecimal("100.00"));

        GameSessionEntity session = new GameSessionEntity();
        session.setStatus("FINISHED");
        session.setWinner(userWithBadges);
        session.setEndTime(Instant.now());
        session = gameSessionRepository.save(session);

        MatchParticipantEntity participant = new MatchParticipantEntity();
        participant.setId(new MatchParticipantId(session.getMatchId(), userWithBadges.getUserId()));
        participant.setMatch(session);
        participant.setUser(userWithBadges);
        participant.setSelectedBallNumber(4);
        participant.setCurrentHp(25);
        participant.setAlive(true);
        participant.setMultiplierWon(new BigDecimal("12.00"));
        matchParticipantRepository.save(participant);
        createRoundSummary(session);

        mockMvc.perform(get("/api/profile/badges")
                .header("Authorization", bearerFor(userWithoutBadges)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.badges.length()").value(5))
            .andExpect(jsonPath("$.badges[0].unlocked").value(false));

        mockMvc.perform(get("/api/profile/badges")
                .header("Authorization", bearerFor(userWithBadges)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.badges.length()").value(5))
            .andExpect(jsonPath("$.badges[0].title").value("Primer Knockout"))
            .andExpect(jsonPath("$.badges[0].unlocked").value(true))
            .andExpect(jsonPath("$.badges[1].title").value("Sangre Fria"))
            .andExpect(jsonPath("$.badges[1].unlocked").value(true));
    }

    @Test
    void statsAndBadgesAreDerivedFromBackendProgress() throws Exception {
        var user = createUser("profile-stats", "profile-stats@test.com", "secret123", "USER", new BigDecimal("100.00"));
        var rival = createUser("rival", "rival@test.com", "secret123", "USER", new BigDecimal("100.00"));

        GameSessionEntity wonSession = new GameSessionEntity();
        wonSession.setStatus("FINISHED");
        wonSession.setWinner(user);
        wonSession.setEndTime(Instant.now());
        wonSession = gameSessionRepository.save(wonSession);
        MatchParticipantEntity wonParticipation = new MatchParticipantEntity();
        wonParticipation.setId(new MatchParticipantId(wonSession.getMatchId(), user.getUserId()));
        wonParticipation.setMatch(wonSession);
        wonParticipation.setUser(user);
        wonParticipation.setSelectedBallNumber(7);
        wonParticipation.setCurrentHp(25);
        wonParticipation.setAlive(true);
        wonParticipation.setMultiplierWon(new BigDecimal("4.00"));
        matchParticipantRepository.save(wonParticipation);
        createRoundSummary(wonSession);

        GameSessionEntity lostSession = new GameSessionEntity();
        lostSession.setStatus("FINISHED");
        lostSession.setWinner(rival);
        lostSession.setEndTime(Instant.now());
        lostSession = gameSessionRepository.save(lostSession);
        MatchParticipantEntity lostParticipation = new MatchParticipantEntity();
        lostParticipation.setId(new MatchParticipantId(lostSession.getMatchId(), user.getUserId()));
        lostParticipation.setMatch(lostSession);
        lostParticipation.setUser(user);
        lostParticipation.setSelectedBallNumber(8);
        lostParticipation.setCurrentHp(0);
        lostParticipation.setAlive(false);
        lostParticipation.setMultiplierWon(new BigDecimal("2.00"));
        matchParticipantRepository.save(lostParticipation);
        createRoundSummary(lostSession);

        GameSessionEntity cancelledSession = new GameSessionEntity();
        cancelledSession.setStatus("MATCHMAKING");
        cancelledSession = gameSessionRepository.save(cancelledSession);
        MatchParticipantEntity cancelledParticipation = new MatchParticipantEntity();
        cancelledParticipation.setId(new MatchParticipantId(cancelledSession.getMatchId(), user.getUserId()));
        cancelledParticipation.setMatch(cancelledSession);
        cancelledParticipation.setUser(user);
        cancelledParticipation.setMultiplierWon(new BigDecimal("500.00"));
        matchParticipantRepository.save(cancelledParticipation);

        GameSessionEntity closedSession = new GameSessionEntity();
        closedSession.setStatus("CLOSED");
        closedSession.setEndTime(Instant.now());
        closedSession = gameSessionRepository.save(closedSession);
        MatchParticipantEntity closedParticipation = new MatchParticipantEntity();
        closedParticipation.setId(new MatchParticipantId(closedSession.getMatchId(), user.getUserId()));
        closedParticipation.setMatch(closedSession);
        closedParticipation.setUser(user);
        closedParticipation.setSelectedBallNumber(9);
        closedParticipation.setMultiplierWon(new BigDecimal("300.00"));
        matchParticipantRepository.save(closedParticipation);

        TransactionLogEntity reward = new TransactionLogEntity();
        reward.setUser(user);
        reward.setType("REWARDED");
        reward.setAmountFiat(new BigDecimal("25.00"));
        reward.setDescription("Test rewarded");
        transactionLogRepository.save(reward);

        mockMvc.perform(get("/api/profile/stats")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stats.ballRoomsPlayed").value(2))
            .andExpect(jsonPath("$.stats.battlesPlayed").value(2))
            .andExpect(jsonPath("$.stats.battlesWon").value(1))
            .andExpect(jsonPath("$.stats.bestMultiplier").value(4.0))
            .andExpect(jsonPath("$.stats.averageMultiplier").value(3.0))
            .andExpect(jsonPath("$.stats.rewardedAdsClaimed").value(1));

        mockMvc.perform(get("/api/profile/badges")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.badges[0].unlocked").value(true))
            .andExpect(jsonPath("$.badges[1].unlocked").value(true))
            .andExpect(jsonPath("$.badges[2].unlocked").value(false));
    }

    @Test
    void statsRemainAfterMatchRowsAreDeleted() throws Exception {
        var user = createUser("profile-archive", "profile-archive@test.com", "secret123", "USER", new BigDecimal("100.00"));
        var rival = createUser("profile-rival", "profile-rival@test.com", "secret123", "USER", new BigDecimal("100.00"));

        GameSessionEntity session = new GameSessionEntity();
        session.setStatus("FINISHED");
        session.setWinner(user);
        session.setEndTime(Instant.now());
        session = gameSessionRepository.save(session);

        MatchParticipantEntity participation = new MatchParticipantEntity();
        participation.setId(new MatchParticipantId(session.getMatchId(), user.getUserId()));
        participation.setMatch(session);
        participation.setUser(user);
        participation.setSelectedBallNumber(12);
        participation.setCurrentHp(20);
        participation.setAlive(true);
        participation.setMultiplierWon(new BigDecimal("2.50"));
        matchParticipantRepository.save(participation);

        MatchParticipantEntity rivalParticipation = new MatchParticipantEntity();
        rivalParticipation.setId(new MatchParticipantId(session.getMatchId(), rival.getUserId()));
        rivalParticipation.setMatch(session);
        rivalParticipation.setUser(rival);
        rivalParticipation.setSelectedBallNumber(13);
        rivalParticipation.setCurrentHp(0);
        rivalParticipation.setAlive(false);
        rivalParticipation.setMultiplierWon(new BigDecimal("1.25"));
        matchParticipantRepository.save(rivalParticipation);
        createRoundSummary(session);

        mockMvc.perform(post("/api/game/matches/{matchId}/close", session.getMatchId())
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk());

        assertThat(matchParticipantRepository.findByIdMatchId(session.getMatchId())).isEmpty();

        mockMvc.perform(get("/api/profile/stats")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stats.ballRoomsPlayed").value(1))
            .andExpect(jsonPath("$.stats.battlesPlayed").value(1))
            .andExpect(jsonPath("$.stats.battlesWon").value(1))
            .andExpect(jsonPath("$.stats.bestMultiplier").value(2.5))
            .andExpect(jsonPath("$.stats.averageMultiplier").value(2.5));
    }

    @Test
    void statsRemainAfterUserAbandonsAnActiveBattle() throws Exception {
        var user = createUser("profile-abandon", "profile-abandon@test.com", "secret123", "USER", new BigDecimal("100.00"));
        var rival = createUser("profile-abandon-rival", "profile-abandon-rival@test.com", "secret123", "USER", new BigDecimal("100.00"));

        GameSessionEntity session = new GameSessionEntity();
        session.setStatus("IN_PROGRESS");
        session = gameSessionRepository.save(session);

        MatchParticipantEntity participation = new MatchParticipantEntity();
        participation.setId(new MatchParticipantId(session.getMatchId(), user.getUserId()));
        participation.setMatch(session);
        participation.setUser(user);
        participation.setSelectedBallNumber(12);
        participation.setCurrentHp(0);
        participation.setAlive(false);
        participation.setMultiplierWon(new BigDecimal("2.50"));
        matchParticipantRepository.save(participation);

        MatchParticipantEntity rivalParticipation = new MatchParticipantEntity();
        rivalParticipation.setId(new MatchParticipantId(session.getMatchId(), rival.getUserId()));
        rivalParticipation.setMatch(session);
        rivalParticipation.setUser(rival);
        rivalParticipation.setSelectedBallNumber(13);
        rivalParticipation.setCurrentHp(20);
        rivalParticipation.setAlive(true);
        rivalParticipation.setMultiplierWon(new BigDecimal("1.25"));
        matchParticipantRepository.save(rivalParticipation);
        createRoundSummary(session);

        mockMvc.perform(post("/api/game/matches/{matchId}/abandon", session.getMatchId())
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/profile/stats")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stats.ballRoomsPlayed").value(1))
            .andExpect(jsonPath("$.stats.battlesPlayed").value(1))
            .andExpect(jsonPath("$.stats.battlesWon").value(0))
            .andExpect(jsonPath("$.stats.bestMultiplier").value(2.5))
            .andExpect(jsonPath("$.stats.averageMultiplier").value(2.5));
    }

    @Test
    void inferredWinnerStatsRemainAfterWinnerLogsOut() throws Exception {
        var user = createUser("profile-winner-logout", "profile-winner-logout@test.com", "secret123", "USER", new BigDecimal("100.00"));
        var rival = createUser("profile-winner-rival", "profile-winner-rival@test.com", "secret123", "USER", new BigDecimal("100.00"));

        GameSessionEntity session = new GameSessionEntity();
        session.setStatus("FINISHED");
        session.setEndTime(Instant.now());
        session = gameSessionRepository.save(session);

        MatchParticipantEntity participation = new MatchParticipantEntity();
        participation.setId(new MatchParticipantId(session.getMatchId(), user.getUserId()));
        participation.setMatch(session);
        participation.setUser(user);
        participation.setSelectedBallNumber(12);
        participation.setCurrentHp(20);
        participation.setAlive(true);
        participation.setMultiplierWon(new BigDecimal("2.50"));
        matchParticipantRepository.save(participation);

        MatchParticipantEntity rivalParticipation = new MatchParticipantEntity();
        rivalParticipation.setId(new MatchParticipantId(session.getMatchId(), rival.getUserId()));
        rivalParticipation.setMatch(session);
        rivalParticipation.setUser(rival);
        rivalParticipation.setSelectedBallNumber(13);
        rivalParticipation.setCurrentHp(0);
        rivalParticipation.setAlive(false);
        rivalParticipation.setMultiplierWon(new BigDecimal("1.25"));
        matchParticipantRepository.save(rivalParticipation);
        createRoundSummary(session);

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk());

        assertThat(transactionLogRepository.countByUserUserIdAndType(user.getUserId(), "PROFILE_BALL_ROOM_PLAYED")).isEqualTo(1);
        assertThat(transactionLogRepository.countByUserUserIdAndType(user.getUserId(), "PROFILE_BATTLE_PLAYED")).isEqualTo(1);
        assertThat(transactionLogRepository.countByUserUserIdAndType(user.getUserId(), "PROFILE_BATTLE_WON")).isEqualTo(1);
        assertThat(transactionLogRepository.findMaxAmountByUserAndType(user.getUserId(), "PROFILE_MULTIPLIER"))
            .isEqualByComparingTo(new BigDecimal("2.50"));
    }

    private void createRoundSummary(GameSessionEntity session) {
        GameSessionEventEntity event = new GameSessionEventEntity();
        event.setMatch(session);
        event.setEventType("ROUND_SUMMARY");
        event.setMessage("Ronda 01");
        gameSessionEventRepository.save(event);
    }
}
