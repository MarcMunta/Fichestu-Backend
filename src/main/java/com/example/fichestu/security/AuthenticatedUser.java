package com.example.fichestu.security;

public record AuthenticatedUser(
    Integer userId,
    String email,
    String username,
    String role
) {
}
