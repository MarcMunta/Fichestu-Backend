package com.example.fichestu.api;

import com.example.fichestu.api.AuthDtos.SessionResponse;
import com.example.fichestu.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public SessionResponse me() {
        return authService.currentSession();
    }
}
