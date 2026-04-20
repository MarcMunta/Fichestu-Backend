package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.TransactionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLogRepository extends JpaRepository<TransactionLogEntity, Integer> {
    long countByUserUserIdAndType(Integer userId, String type);
}
