package com.example.fichestu.security;

import com.example.fichestu.persistence.entity.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
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
            .setIssuedAt(Date.from(issuedAt))
            .setExpiration(Date.from(expiresAt))
            .signWith(signingKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        return parseClaims(token)
            .map(claims -> new AuthenticatedUser(claims.userId(), claims.email(), claims.username(), "USER"));
    }

    public Optional<JwtTokenClaims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(signingKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

            Integer userId = claims.get("uid", Integer.class);
            String email = claims.get("email", String.class);
            String username = claims.get("username", String.class);
            Date expiration = claims.getExpiration();

            if (userId == null || email == null || username == null || expiration == null) {
                return Optional.empty();
            }

            return Optional.of(new JwtTokenClaims(userId, email, username, expiration.toInstant()));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private Key signingKey() {
        byte[] secretBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

}
