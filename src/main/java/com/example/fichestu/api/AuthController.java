package com.example.fichestu.api;

import com.example.fichestu.api.AuthDtos.AuthResponse;
import com.example.fichestu.api.AuthDtos.GoogleRequest;
import com.example.fichestu.api.AuthDtos.LoginRequest;
import com.example.fichestu.api.AuthDtos.PasswordResetConfirmRequest;
import com.example.fichestu.api.AuthDtos.PasswordResetRequest;
import com.example.fichestu.api.AuthDtos.RegisterRequest;
import com.example.fichestu.api.AuthDtos.SessionResponse;
import com.example.fichestu.api.ProfileDtos.GenericResponse;
import com.example.fichestu.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse googleLogin(@Valid @RequestBody GoogleRequest request) {
        return authService.loginWithGoogle(request.getIdToken());
    }

    @PostMapping("/password-reset/request")
    public GenericResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return authService.requestPasswordReset(request);
    }

    @PostMapping("/password-reset/confirm")
    public GenericResponse confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        return authService.confirmPasswordReset(request);
    }

    @GetMapping("/me")
    public SessionResponse me() {
        return authService.currentSession();
    }

    @GetMapping("/admin/ping")
    @PreAuthorize("hasRole('ADMIN')")
    public GenericResponse adminPing() {
        return new GenericResponse("Admin autorizado", true);
    }
}
