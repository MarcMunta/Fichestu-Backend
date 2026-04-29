package com.example.fichestu.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.fichestu.persistence.entity.GameSessionEntity;

import jakarta.persistence.LockModeType;

public interface GameSessionRepository extends JpaRepository<GameSessionEntity, Integer> {
    List<GameSessionEntity> findByStatusOrderByMatchIdAsc(String status);

    @Query(value = "select * from game_sessions where match_id = :matchId for update", nativeQuery = true)
    Optional<GameSessionEntity> findByMatchIdForUpdate(@Param("matchId") Integer matchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameSessionEntity g where g.status in ('WAITING','PICKING') and (g.selectionDeadline is null or g.selectionDeadline > :now) order by g.matchId asc")
    List<GameSessionEntity> findJoinableRoomsForUpdate(@Param("now") java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameSessionEntity g where g.status in ('WAITING','PICKING','READY_REVEAL') and g.selectionDeadline is not null and g.selectionDeadline <= :now")
    List<GameSessionEntity> findExpiredSelectionRoomsForUpdate(@Param("now") java.time.Instant now);
}
