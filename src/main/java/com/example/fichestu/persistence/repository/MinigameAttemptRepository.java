package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.MinigameAttemptEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinigameAttemptRepository extends JpaRepository<MinigameAttemptEntity, Integer> {
    Optional<MinigameAttemptEntity> findTopByUserUserIdAndGameTypeAndPaymentTypeOrderByStartedAtDesc(
        Integer userId,
        String gameType,
        String paymentType
    );

    List<MinigameAttemptEntity> findTop10ByUserUserIdOrderByStartedAtDesc(Integer userId);
}
