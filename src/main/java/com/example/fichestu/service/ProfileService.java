package com.example.fichestu.service;

import com.example.fichestu.api.ProfileDtos.ChangePasswordRequest;
import com.example.fichestu.api.ProfileDtos.GenericResponse;
import com.example.fichestu.api.ProfileDtos.ProfileResponse;
import com.example.fichestu.api.ProfileDtos.UpdateProfileRequest;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String authHeader) {
        UserEntity user = resolveUser(authHeader);
        return toProfileResponse(user, "Perfil cargado");
    }

    @Transactional
    public ProfileResponse updateProfile(String authHeader, UpdateProfileRequest request) {
        UserEntity user = resolveUser(authHeader);

        String normalizedUsername = request.getUsername().trim();
        String normalizedEmail = request.getEmail().trim().toLowerCase();

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
        userRepository.save(user);

        return toProfileResponse(user, "Perfil actualizado");
    }

    @Transactional
    public GenericResponse changePassword(String authHeader, ChangePasswordRequest request) {
        UserEntity user = resolveUser(authHeader);

        String newPassword = request.getNewPassword();
        String confirm = request.getConfirmPassword();

        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contraseña es obligatoria");
        }

        if (!newPassword.equals(confirm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las contraseñas no coinciden");
        }

        if (newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return new GenericResponse("Contraseña actualizada", true);
    }

    private UserEntity resolveUser(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
        }

        String token = authHeader.trim();
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }

        if (!token.startsWith("user-")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
        }

        try {
            Integer userId = Integer.parseInt(token.substring("user-".length()));
            return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida"));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
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
