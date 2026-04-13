package com.example.fichestu.service;

import com.example.fichestu.api.AuthDtos.AuthResponse;
import com.example.fichestu.api.AuthDtos.LoginRequest;
import com.example.fichestu.api.AuthDtos.RegisterRequest;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String GOOGLE_CLIENT_ID_WEB = "376595931736-ts5451g69bk8rd6re82o6ln1p28m4i2l.apps.googleusercontent.com";
    private final String GOOGLE_CLIENT_ID_ANDROID = "376595931736-6oski1i5s8h2dlepv04hhf3upq49jp51.apps.googleusercontent.com";
    
    
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

        return rawPassword.equals(storedPasswordHash);
    }
    
    
    @Transactional
    public AuthResponse loginWithGoogle(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), 
                    new GsonFactory()
                )
            	.setAudience(Arrays.asList(GOOGLE_CLIENT_ID_WEB, GOOGLE_CLIENT_ID_ANDROID))
                .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            
            if (idToken == null) {
                System.out.println("ERROR: El verificado ha devuelto NULL. Revisa los IDs o la hora del sistema.");
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de Google inválido");
            }

            Payload payload = idToken.getPayload();
            String email = payload.getEmail().toLowerCase();

            System.out.println("Usuario verificado correctamente: " + email);

            UserEntity user = userRepository.findByEmail(email).orElseGet(() -> {
                UserEntity newUser = new UserEntity();
                newUser.setEmail(email);
                String name = (String) payload.get("name");
                newUser.setUsername(name != null ? name : email.split("@")[0]); 
                newUser.setPasswordHash("GOOGLE_AUTH"); 
                newUser.setProfilePicUrl((String) payload.get("picture"));
                newUser.setFiatBalance(BigDecimal.ZERO);
                newUser.setRole("USER");
                return userRepository.save(newUser);
            });

            String sessionToken = "user-" + user.getUserId(); 
            return new AuthResponse(sessionToken, "Login con Google exitoso", true);

        } catch (Exception e) {
            e.printStackTrace(); 
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Error de validación: " + e.getMessage());
        }
    }
}
