package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.GameSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSessionEntity, Integer> {
}
