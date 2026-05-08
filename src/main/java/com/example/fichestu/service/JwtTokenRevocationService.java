package com.example.fichestu.service;

import com.example.fichestu.persistence.entity.RevokedJwtTokenEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import com.example.fichestu.persistence.repository.RevokedJwtTokenRepository;
import com.example.fichestu.persistence.repository.UserRepository;
import com.example.fichestu.security.JwtService;
import com.example.fichestu.security.JwtTokenClaims;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JwtTokenRevocationService {

    private final RevokedJwtTokenRepository revokedJwtTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public JwtTokenRevocationService(
        RevokedJwtTokenRepository revokedJwtTokenRepository,
        UserRepository userRepository,
        JwtService jwtService
    ) {
        this.revokedJwtTokenRepository = revokedJwtTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        return revokedJwtTokenRepository.existsByTokenHashAndExpiresAtAfter(jwtService.hashToken(token), Instant.now());
    }

    @Transactional
    public void revoke(String token, String reason) {
        JwtTokenClaims claims = jwtService.parseClaims(token).orElse(null);
        if (claims == null || claims.expiresAt().isBefore(Instant.now())) {
            return;
        }

        revokedJwtTokenRepository.deleteByExpiresAtBefore(Instant.now());

        RevokedJwtTokenEntity revokedToken = new RevokedJwtTokenEntity();
        revokedToken.setTokenHash(jwtService.hashToken(token));
        revokedToken.setExpiresAt(claims.expiresAt());
        revokedToken.setReason(reason);

        UserEntity user = userRepository.findById(claims.userId()).orElse(null);
        revokedToken.setUser(user);

        try {
            revokedJwtTokenRepository.save(revokedToken);
        } catch (DataIntegrityViolationException ignored) {
            // Idempotent logout: repeated requests with same token stay successful.
        }
    }
}
