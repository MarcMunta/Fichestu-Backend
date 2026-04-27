package com.example.fichestu;

import com.example.fichestu.persistence.entity.GameSessionEntity;
import com.example.fichestu.persistence.entity.MatchParticipantEntity;
import com.example.fichestu.persistence.entity.MatchParticipantId;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
        userRepository.save(user);

        mockMvc.perform(get("/api/profile")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.email").value("alice@test.com"))
            .andExpect(jsonPath("$.profilePicUrl").value("https://example.com/avatar.png"));
    }

    @Test
    void unauthenticatedProfileFetchIsRejected() throws Exception {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Sesion invalida"));
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
            .andExpect(jsonPath("$.message").value("El username ya esta en uso"));
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
            .andExpect(jsonPath("$.message").value("Contraseña actualizada"));

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
        session = gameSessionRepository.save(session);

        MatchParticipantEntity participant = new MatchParticipantEntity();
        participant.setId(new MatchParticipantId(session.getMatchId(), userWithBadges.getUserId()));
        participant.setMatch(session);
        participant.setUser(userWithBadges);
        participant.setCurrentHp(25);
        participant.setAlive(true);
        participant.setMultiplierWon(new BigDecimal("12.00"));
        matchParticipantRepository.save(participant);

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
}
