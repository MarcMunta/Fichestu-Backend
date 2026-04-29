package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.GameSessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSessionEntity, Integer> {
    List<GameSessionEntity> findByStatusOrderByMatchIdAsc(String status);

    @Query(value = "select * from game_sessions where match_id = :matchId for update", nativeQuery = true)
    Optional<GameSessionEntity> findByMatchIdForUpdate(@Param("matchId") Integer matchId);
}
