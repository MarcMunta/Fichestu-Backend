package com.example.fichestu.security;

import java.time.Instant;

public record JwtTokenClaims(
    Integer userId,
    String email,
    String username,
    Instant expiresAt
) {
}
