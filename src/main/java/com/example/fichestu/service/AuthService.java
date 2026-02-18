package com.example.fichestu.service;

import com.example.fichestu.api.AuthDtos.AuthResponse;
import com.example.fichestu.api.AuthDtos.LoginRequest;
import com.example.fichestu.api.AuthDtos.RegisterRequest;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.UserRepository;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedUsername = request.getUsername().trim();

        if (normalizedUsername.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El username es obligatorio");
        }

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El email ya esta registrado");
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El username ya esta registrado");
        }

        UserEntity user = new UserEntity();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setProfilePicUrl(null);
        user.setFiatBalance(BigDecimal.ZERO);
        user.setRole("USER");

        userRepository.save(user);

        return new AuthResponse(null, "Registro completado", true);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"));

        if (!passwordMatches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
        }

        String token = "user-" + user.getUserId();
        return new AuthResponse(token, "Login correcto", true);
    }

    private boolean passwordMatches(String rawPassword, String storedPasswordHash) {
        if (storedPasswordHash == null || storedPasswordHash.isEmpty()) {
            return false;
        }

        if (storedPasswordHash.startsWith("$2a$") || storedPasswordHash.startsWith("$2b$")) {
            return passwordEncoder.matches(rawPassword, storedPasswordHash);
        }

        // Compatibility for seed users that still have plain-text passwords.
        return rawPassword.equals(storedPasswordHash);
    }
}
