package com.example.fichestu.service;

import com.example.fichestu.api.AuthDtos.AuthResponse;
import com.example.fichestu.api.AuthDtos.LoginRequest;
import com.example.fichestu.api.AuthDtos.RegisterRequest;
import com.example.fichestu.api.AuthDtos.SessionResponse;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.security.CurrentUserService;
import com.example.fichestu.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.math.BigDecimal;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final BigDecimal INITIAL_FIAT_BALANCE = new BigDecimal("100.00");

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUserService currentUserService;
    private final String googleClientIdWeb = "376595931736-ts5451g69bk8rd6re82o6ln1p28m4i2l.apps.googleusercontent.com";
    private final String googleClientIdAndroid = "376595931736-6oski1i5s8h2dlepv04hhf3upq49jp51.apps.googleusercontent.com";

    public AuthService(
        UserRepository userRepository,
        BCryptPasswordEncoder passwordEncoder,
        JwtService jwtService,
        CurrentUserService currentUserService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.currentUserService = currentUserService;
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
                .setAudience(Arrays.asList(googleClientIdWeb, googleClientIdAndroid))
                .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google invalido");
            }

            Payload payload = idToken.getPayload();
            String email = payload.getEmail().toLowerCase();

            String picture = (String) payload.get("picture");
            UserEntity user = userRepository.findByEmail(email).orElseGet(() -> {
                UserEntity newUser = new UserEntity();
                newUser.setEmail(email);
                String name = (String) payload.get("name");
                newUser.setUsername(resolveAvailableUsername(name != null ? name : email.split("@")[0]));
                newUser.setPasswordHash(null);
                newUser.setProfilePicUrl(picture);
                newUser.setFiatBalance(INITIAL_FIAT_BALANCE);
                newUser.setRole("USER");
                return userRepository.save(newUser);
            });

            return new AuthResponse(jwtService.generateToken(user), "Login con Google exitoso", true);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Error de validacion: " + ex.getMessage());
        }
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
