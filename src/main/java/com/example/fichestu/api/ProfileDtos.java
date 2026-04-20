package com.example.fichestu.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileResponse(
        String message,
        Boolean success,
        String username,
        String email,
        String role,
        String profilePicUrl
    ) {
    }

    public static class UpdateProfileRequest {
        @NotBlank
        private String username;

        @NotBlank
        @Email
        private String email;

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
    }

    public static class ChangePasswordRequest {
        @NotBlank
        private String newPassword;

        @NotBlank
        private String confirmPassword;

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }

        public String getConfirmPassword() {
            return confirmPassword;
        }

        public void setConfirmPassword(String confirmPassword) {
            this.confirmPassword = confirmPassword;
        }
    }

    public record GenericResponse(
        String message,
        Boolean success
    ) {
    }
}
