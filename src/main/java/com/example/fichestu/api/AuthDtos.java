package com.example.fichestu.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public static class RegisterRequest {
        @NotBlank
        @Size(max = 50)
        @JsonAlias({"displayName", "name"})
        private String username;

        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class LoginRequest {
        @NotBlank
        @Email
        private String email;

        @NotBlank
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public record AuthResponse(
        String token,
        String message,
        Boolean success
    ) {
    }

    public static class GoogleRequest {
        @NotBlank
        private String idToken;

        public String getIdToken() { return idToken; }

        public void setIdToken(String idToken) { this.idToken = idToken; }
    }

    public record SessionResponse(
        String message,
        Boolean success,
        Integer userId,
        String username,
        String email,
        String role
    ) {
    }
}
