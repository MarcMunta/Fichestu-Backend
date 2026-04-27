package com.example.fichestu;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTests extends IntegrationTestSupport {

    @Test
    void registerPersistsPasswordAsHash() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "nuevo",
                    "email", "nuevo@test.com",
                    "password", "secret123"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Registro completado"));

        var user = userRepository.findByEmail("nuevo@test.com").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", user.getPasswordHash())).isTrue();
        assertThat(user.getFiatBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void loginReturnsSignedJwt() throws Exception {
        createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "alice@test.com",
                    "password", "secret123"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.token").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String token = objectMapper.readTree(body).get("token").asText();
        assertThat(token).contains(".");
        assertThat(token).doesNotStartWith("user-");
        assertThat(jwtService.parseToken(token)).isPresent();
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "email", "alice@test.com",
                    "password", "bad-pass"
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Credenciales invalidas"));
    }

    @Test
    void protectedEndpointAcceptsValidJwt() throws Exception {
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.getUserId()))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void protectedEndpointRejectsMissingJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Sesion invalida"));
    }

    @Test
    void protectedEndpointRejectsInvalidOrExpiredJwt() throws Exception {
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));
        String expiredToken = jwtService.generateToken(user, Duration.ofSeconds(-60));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminRouteRejectsNonAdminUser() throws Exception {
        var user = createUser("alice", "alice@test.com", "secret123", "USER", new BigDecimal("100.00"));

        mockMvc.perform(get("/api/auth/admin/ping")
                .header("Authorization", bearerFor(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("No tienes permisos para esta operacion"));
    }
}
