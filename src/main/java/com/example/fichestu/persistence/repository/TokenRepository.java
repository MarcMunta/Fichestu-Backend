package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.TokenEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRepository extends JpaRepository<TokenEntity, Integer> {
    List<TokenEntity> findAllByOrderByTokenIdAsc();

    Optional<TokenEntity> findByNameIgnoreCase(String name);
}
