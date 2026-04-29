package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.PasswordResetTokenEntity;
import com.example.fichestu.persistence.entity.UserEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, Integer> {
    List<PasswordResetTokenEntity> findByUserAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
        UserEntity user,
        Instant now
    );

    void deleteByUser(UserEntity user);
}
