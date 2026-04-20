package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.TokenPriceHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenPriceHistoryRepository extends JpaRepository<TokenPriceHistoryEntity, Integer> {
    List<TokenPriceHistoryEntity> findTop28ByTokenTokenIdOrderByRecordedAtDesc(Integer tokenId);
}
