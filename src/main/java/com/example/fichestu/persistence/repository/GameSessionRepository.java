package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.GameSessionEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameSessionRepository extends JpaRepository<GameSessionEntity, Integer> {
    List<GameSessionEntity> findByStatusOrderByMatchIdAsc(String status);

    @Query("""
        select session.matchId
        from GameSessionEntity session
        where (
            session.status in ('MATCHMAKING', 'PICKING')
            and session.matchmakingDeadline is not null
            and session.matchmakingDeadline <= :now
        ) or (
            session.status = 'IN_PROGRESS'
            and session.battleRoundDeadline is not null
            and session.battleRoundDeadline <= :now
        )
        order by session.matchId asc
        """)
    List<Integer> findTimedOutActiveMatchIds(@Param("now") Instant now);

    @Query(value = "select * from game_sessions where match_id = :matchId for update", nativeQuery = true)
    Optional<GameSessionEntity> findByMatchIdForUpdate(@Param("matchId") Integer matchId);
}
