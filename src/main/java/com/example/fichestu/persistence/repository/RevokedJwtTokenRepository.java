package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.RevokedJwtTokenEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedJwtTokenRepository extends JpaRepository<RevokedJwtTokenEntity, Integer> {
    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);

    void deleteByExpiresAtBefore(Instant now);
}
