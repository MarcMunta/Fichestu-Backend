package com.example.fichestu.security;

import com.example.fichestu.persistence.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(UserEntity user) {
        return generateToken(user, Duration.ofSeconds(jwtProperties.getExpirationSeconds()));
    }

    public String generateToken(UserEntity user, Duration ttl) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);

        return Jwts.builder()
            .setSubject(user.getEmail())
            .claim("uid", user.getUserId())
            .claim("email", user.getEmail())
            .claim("username", user.getUsername())
            .claim("role", normalizeRole(user.getRole()))
            .setIssuedAt(Date.from(issuedAt))
            .setExpiration(Date.from(expiresAt))
            .signWith(signingKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

            Integer userId = claims.get("uid", Integer.class);
            String email = claims.get("email", String.class);
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            if (userId == null || email == null || username == null || role == null) {
                return Optional.empty();
            }

            return Optional.of(new AuthenticatedUser(userId, email, username, normalizeRole(role)));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Key signingKey() {
        byte[] secretBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    private String normalizeRole(String role) {
        return role == null || role.isBlank() ? "USER" : role.trim().toUpperCase();
    }
}
