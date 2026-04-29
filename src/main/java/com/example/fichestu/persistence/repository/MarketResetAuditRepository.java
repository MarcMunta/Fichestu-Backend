package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.MarketResetAuditEntity;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketResetAuditRepository extends JpaRepository<MarketResetAuditEntity, Integer> {
    boolean existsByBusinessDate(LocalDate businessDate);
}
