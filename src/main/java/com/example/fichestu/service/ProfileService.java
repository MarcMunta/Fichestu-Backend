package com.example.fichestu.service;

import com.example.fichestu.api.ProfileDtos.BadgeListResponse;
import com.example.fichestu.api.ProfileDtos.ChangePasswordRequest;
import com.example.fichestu.api.ProfileDtos.GenericResponse;
import com.example.fichestu.api.ProfileDtos.ProfileResponse;
import com.example.fichestu.api.ProfileDtos.StatsResponse;
import com.example.fichestu.api.ProfileDtos.UpdateProfileRequest;
import com.example.fichestu.api.ProfileDtos.UpdateLanguageRequest;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.security.CurrentUserService;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProfileService {

    private static final Path AVATAR_UPLOAD_DIR = Path.of("uploads", "profile-pictures");
    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

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
    public ProfileResponse uploadAvatar(MultipartFile avatar) {
        UserEntity user = currentUserService.requireUserEntity();

        if (avatar == null || avatar.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen es obligatoria");
        }

        String contentType = avatar.getContentType() == null
            ? ""
            : avatar.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_AVATAR_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de imagen no permitido");
        }

        String extension = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String fileName = "user-" + user.getUserId() + "-" + UUID.randomUUID() + extension;

        try {
            Files.createDirectories(AVATAR_UPLOAD_DIR);
            Path target = AVATAR_UPLOAD_DIR.resolve(fileName).normalize();
            Files.copy(avatar.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen");
        }

        user.setProfilePicUrl("/api/profile/avatar/" + fileName);
        userRepository.save(user);

        return toProfileResponse(user, "Foto de perfil actualizada");
    }

    public ResponseEntity<Resource> getAvatar(String fileName) {
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de imagen invalido");
        }

        try {
            Path file = AVATAR_UPLOAD_DIR.resolve(fileName).normalize();
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
            }
            return ResponseEntity.ok()
                .contentType(resolveMediaType(fileName))
                .body(resource);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
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
    public ProfileResponse updateLanguage(UpdateLanguageRequest request) {
        UserEntity user = currentUserService.requireUserEntity();
        String language = normalizeLanguage(request.getLanguage());
        user.setPreferredLanguage(language);
        userRepository.save(user);
        return toProfileResponse(user, "Idioma actualizado");
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

        String trimmed = rawValue.trim();
        if (trimmed.startsWith("preset:")) {
            String presetId = trimmed.substring("preset:".length());
            if (!presetId.matches("[a-z0-9-]{1,40}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar predefinido no valido");
            }
            return trimmed;
        }

        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen de perfil debe usar http o https");
            }
            return trimmed;
        } catch (URISyntaxException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen de perfil no es una URL valida");
        }
    }

    private ProfileResponse toProfileResponse(UserEntity user, String message) {
        boolean hasPassword = user.getPasswordHash() != null && !user.getPasswordHash().isBlank();
        return new ProfileResponse(
            message,
            true,
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.getProfilePicUrl(),
            hasPassword,
            normalizeLanguage(user.getPreferredLanguage())
        );
    }

    private String normalizeLanguage(String rawLanguage) {
        if (rawLanguage == null) {
            return "es";
        }
        String normalized = rawLanguage.trim().toLowerCase(Locale.ROOT);
        if ("ca".equals(normalized) || "en".equals(normalized) || "es".equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idioma no soportado");
    }

    private MediaType resolveMediaType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
