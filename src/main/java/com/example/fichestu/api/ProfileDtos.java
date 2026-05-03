package com.example.fichestu.api;

import com.example.fichestu.api.GameDtos.BadgeDto;
import com.example.fichestu.api.GameDtos.ProfileStatsDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileResponse(
        String message,
        Boolean success,
        String username,
        String email,
        String role,
        String profilePicUrl,
        Boolean hasPassword
    ) {
    }

    public static class UpdateProfileRequest {
        @NotBlank
        private String username;

        @NotBlank
        @Email
        private String email;

        @Size(max = 512)
        private String profilePicUrl;

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

        public String getProfilePicUrl() {
            return profilePicUrl;
        }

        public void setProfilePicUrl(String profilePicUrl) {
            this.profilePicUrl = profilePicUrl;
        }
    }

    public static class ChangePasswordRequest {
        private String currentPassword;

        @NotBlank
        private String newPassword;

        @NotBlank
        private String confirmPassword;

        public String getCurrentPassword() {
            return currentPassword;
        }

        public void setCurrentPassword(String currentPassword) {
            this.currentPassword = currentPassword;
        }

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

    public record BadgeListResponse(
        String message,
        Boolean success,
        List<BadgeDto> badges
    ) {
    }

    public record StatsResponse(
        String message,
        Boolean success,
        ProfileStatsDto stats
    ) {
    }
}
