package com.example.fichestu.service;

import com.example.fichestu.api.ProfileDtos.BadgeListResponse;
import com.example.fichestu.api.ProfileDtos.ChangePasswordRequest;
import com.example.fichestu.api.ProfileDtos.GenericResponse;
import com.example.fichestu.api.ProfileDtos.ProfileResponse;
import com.example.fichestu.api.ProfileDtos.StatsResponse;
import com.example.fichestu.api.ProfileDtos.UpdateProfileRequest;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.security.CurrentUserService;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final PlayerProfileReadService playerProfileReadService;
    private final BCryptPasswordEncoder passwordEncoder;

    public ProfileService(
        UserRepository userRepository,
        CurrentUserService currentUserService,
        PlayerProfileReadService playerProfileReadService,
        BCryptPasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.playerProfileReadService = playerProfileReadService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        return toProfileResponse(currentUserService.requireUserEntity(), "Perfil cargado");
    }

    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest request) {
        UserEntity user = currentUserService.requireUserEntity();

        String normalizedUsername = request.getUsername().trim();
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String profilePicUrl = request.getProfilePicUrl() == null
            ? user.getProfilePicUrl()
            : normalizeProfilePicUrl(request.getProfilePicUrl());

        if (normalizedUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El username es obligatorio");
        }

        if (!normalizedUsername.equals(user.getUsername()) && userRepository.existsByUsername(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El username ya esta en uso");
        }

        if (!normalizedEmail.equals(user.getEmail()) && userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya esta en uso");
        }

        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setProfilePicUrl(profilePicUrl);
        userRepository.save(user);

        return toProfileResponse(user, "Perfil actualizado");
    }

    @Transactional
    public GenericResponse changePassword(ChangePasswordRequest request) {
        UserEntity user = currentUserService.requireUserEntity();

        String currentPassword = request.getCurrentPassword();
        String newPassword = request.getNewPassword();
        String confirm = request.getConfirmPassword();

        boolean hasLocalPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        if (hasLocalPassword) {
            if (currentPassword == null || currentPassword.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Introduce la contrasena actual");
            }
            if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena actual no es correcta");
            }
        }

        if (!newPassword.equals(confirm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las contrasenas no coinciden");
        }

        if (newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena debe tener al menos 6 caracteres");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return new GenericResponse(hasLocalPassword ? "Contrasena actualizada" : "Contrasena local creada", true);
    }

    @Transactional
    public BadgeListResponse getBadges() {
        UserEntity user = currentUserService.requireUserEntity();
        return new BadgeListResponse(
            "Badges cargados",
            true,
            playerProfileReadService.loadBadgesForUser(user.getUserId())
        );
    }

    @Transactional(readOnly = true)
    public StatsResponse getStats() {
        UserEntity user = currentUserService.requireUserEntity();
        return new StatsResponse(
            "Estadisticas cargadas",
            true,
            playerProfileReadService.loadStatsForUser(user.getUserId())
        );
    }

    private String normalizeProfilePicUrl(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            URI uri = new URI(rawValue.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen de perfil debe usar http o https");
            }
            return rawValue.trim();
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen de perfil no es una URL valida");
        }
    }

    private ProfileResponse toProfileResponse(UserEntity user, String message) {
        return new ProfileResponse(
            message,
            true,
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.getProfilePicUrl()
        );
    }
}
