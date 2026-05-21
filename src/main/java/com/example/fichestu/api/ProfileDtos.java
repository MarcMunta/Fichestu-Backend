package com.example.fichestu.api;

import com.example.fichestu.api.GameDtos.BadgeDto;
import com.example.fichestu.api.GameDtos.ProfileStatsDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
        Boolean hasPassword,
        String preferredLanguage,
        String profileCardBackground,
        String profilePageBackground
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

    public static class UpdateProfileStyleRequest {
        @Size(max = 80)
        private String profileCardBackground;

        @Size(max = 512)
        private String profilePageBackground;

        public String getProfileCardBackground() {
            return profileCardBackground;
        }

        public void setProfileCardBackground(String profileCardBackground) {
            this.profileCardBackground = profileCardBackground;
        }

        public String getProfilePageBackground() {
            return profilePageBackground;
        }

        public void setProfilePageBackground(String profilePageBackground) {
            this.profilePageBackground = profilePageBackground;
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

    public static class UpdateLanguageRequest {
        @NotBlank
        @Pattern(regexp = "es|ca|en")
        private String language;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
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
