package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.TransactionLogEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLogRepository extends JpaRepository<TransactionLogEntity, Integer> {
    long countByUserUserIdAndType(Integer userId, String type);

    Optional<TransactionLogEntity> findTopByUserUserIdAndTypeOrderByCreatedAtDesc(Integer userId, String type);

    boolean existsByUserUserIdAndTypeAndDescription(Integer userId, String type, String description);

    List<TransactionLogEntity> findTop20ByUserUserIdOrderByCreatedAtDesc(Integer userId);
}
