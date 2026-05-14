package com.example.fichestu.service;

import com.example.fichestu.api.AuthDtos.AuthResponse;
import com.example.fichestu.api.AuthDtos.LoginRequest;
import com.example.fichestu.api.AuthDtos.PasswordResetConfirmRequest;
import com.example.fichestu.api.AuthDtos.PasswordResetRequest;
import com.example.fichestu.api.AuthDtos.RegisterRequest;
import com.example.fichestu.api.AuthDtos.SessionResponse;
import com.example.fichestu.api.ProfileDtos.GenericResponse;
import com.example.fichestu.persistence.entity.PasswordResetTokenEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.PasswordResetTokenRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.security.CurrentUserService;
import com.example.fichestu.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final BigDecimal INITIAL_FIAT_BALANCE = new BigDecimal("100.00");
    private static final int RESET_TOKEN_BOUND = 1_000_000;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtTokenRevocationService jwtTokenRevocationService;
    private final CurrentUserService currentUserService;
    private final PasswordResetMailService passwordResetMailService;
    private final AutomatedEmailService automatedEmailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String googleClientIdWeb = "376595931736-ts5451g69bk8rd6re82o6ln1p28m4i2l.apps.googleusercontent.com";
    private final String googleClientIdAndroid = "376595931736-6oski1i5s8h2dlepv04hhf3upq49jp51.apps.googleusercontent.com";
    private final String googleClientIdSharedDebug = "376595931736-vooam8kfuqrv1u63tcao1hm9hq56noo5.apps.googleusercontent.com";

    @Value("${app.password-reset.expiration-minutes:15}")
    private long passwordResetExpirationMinutes;

    public AuthService(
        UserRepository userRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        BCryptPasswordEncoder passwordEncoder,
        JwtService jwtService,
        JwtTokenRevocationService jwtTokenRevocationService,
        CurrentUserService currentUserService,
        PasswordResetMailService passwordResetMailService,
        AutomatedEmailService automatedEmailService
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtTokenRevocationService = jwtTokenRevocationService;
        this.currentUserService = currentUserService;
        this.passwordResetMailService = passwordResetMailService;
        this.automatedEmailService = automatedEmailService;
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
        user.setFiatBalance(INITIAL_FIAT_BALANCE);
        user.setRole("USER");

        userRepository.save(user);
        automatedEmailService.sendRegistrationEmail(user);

        return new AuthResponse(null, "Registro completado", true);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
        }

        return new AuthResponse(jwtService.generateToken(user), "Login correcto", true);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                new GsonFactory()
            )
                .setAudience(Arrays.asList(googleClientIdWeb, googleClientIdAndroid, googleClientIdSharedDebug))
                .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google invalido");
            }

            Payload payload = idToken.getPayload();
            String email = payload.getEmail().toLowerCase();

            String picture = (String) payload.get("picture");
            AtomicBoolean created = new AtomicBoolean(false);
            UserEntity user = userRepository.findByEmail(email).orElseGet(() -> {
                UserEntity newUser = new UserEntity();
                newUser.setEmail(email);
                String name = (String) payload.get("name");
                newUser.setUsername(resolveAvailableUsername(name != null ? name : email.split("@")[0]));
                newUser.setPasswordHash(null);
                newUser.setProfilePicUrl(picture);
                newUser.setFiatBalance(INITIAL_FIAT_BALANCE);
                newUser.setRole("USER");
                created.set(true);
                return userRepository.save(newUser);
            });
            if (created.get()) {
                automatedEmailService.sendRegistrationEmail(user);
            }

            return new AuthResponse(jwtService.generateToken(user), "Login con Google exitoso", true);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Error de validacion: " + ex.getMessage());
        }
    }

    @Transactional
    public GenericResponse logout(String authorization) {
        String token = extractBearerToken(authorization);
        jwtTokenRevocationService.revoke(token, "LOGOUT");
        return new GenericResponse("Sesion cerrada", true);
    }

    @Transactional(readOnly = true)
    public SessionResponse currentSession() {
        UserEntity user = currentUserService.requireUserEntity();
        return new SessionResponse(
            "Sesion valida",
            true,
            user.getUserId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole()
        );
    }

    @Transactional
    public GenericResponse requestPasswordReset(PasswordResetRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            passwordResetTokenRepository.deleteByUser(user);

            String token = String.format("%06d", secureRandom.nextInt(RESET_TOKEN_BOUND));
            PasswordResetTokenEntity resetToken = new PasswordResetTokenEntity();
            resetToken.setUser(user);
            resetToken.setTokenHash(passwordEncoder.encode(token));
            resetToken.setExpiresAt(Instant.now().plus(Duration.ofMinutes(passwordResetExpirationMinutes)));
            passwordResetTokenRepository.save(resetToken);

            passwordResetMailService.sendResetToken(user.getEmail(), token, passwordResetExpirationMinutes);
        });

        return new GenericResponse(
            "Si el email existe, recibiras instrucciones para restablecer la contrasena",
            true
        );
    }

    @Transactional
    public GenericResponse confirmPasswordReset(PasswordResetConfirmRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String token = request.getToken().trim();
        String newPassword = request.getNewPassword();

        if (!newPassword.equals(request.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las contrasenas no coinciden");
        }
        if (newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena debe tener al menos 6 caracteres");
        }

        UserEntity user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(this::invalidOrExpiredToken);

        PasswordResetTokenEntity resetToken = findMatchingResetToken(user, token)
            .orElseThrow(this::invalidOrExpiredToken);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.setUsedAt(Instant.now());
        userRepository.save(user);
        passwordResetTokenRepository.save(resetToken);

        return new GenericResponse("Contrasena actualizada correctamente", true);
    }

    private Optional<PasswordResetTokenEntity> findMatchingResetToken(UserEntity user, String rawToken) {
        return passwordResetTokenRepository
            .findByUserAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, Instant.now())
            .stream()
            .filter(candidate -> passwordEncoder.matches(rawToken, candidate.getTokenHash()))
            .findFirst();
    }

    private ResponseStatusException invalidOrExpiredToken() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalido o caducado");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
        }
        String token = authorization.substring(7).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
        }
        return token;
    }

    private String resolveAvailableUsername(String baseCandidate) {
        String candidate = baseCandidate == null ? "Jugador" : baseCandidate.trim();
        if (candidate.isBlank()) {
            candidate = "Jugador";
        }

        if (!userRepository.existsByUsername(candidate)) {
            return candidate;
        }

        for (int suffix = 2; suffix < 10_000; suffix++) {
            String alternative = candidate + suffix;
            if (!userRepository.existsByUsername(alternative)) {
                return alternative;
            }
        }

        throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo generar un username unico");
    }
}
