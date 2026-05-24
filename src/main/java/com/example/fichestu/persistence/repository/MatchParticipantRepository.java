package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.MatchParticipantEntity;
import com.example.fichestu.persistence.entity.MatchParticipantId;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipantEntity, MatchParticipantId> {
    List<MatchParticipantEntity> findByIdMatchId(Integer matchId);

    List<MatchParticipantEntity> findByIdUserId(Integer userId);

    long countByIdMatchId(Integer matchId);

    long countByIdMatchIdAndSelectedBallNumberIsNotNull(Integer matchId);

    boolean existsByIdMatchIdAndIdUserId(Integer matchId, Integer userId);

    @Query("""
        select count(distinct participant.match.matchId)
        from MatchParticipantEntity participant
        where participant.id.userId = :userId
          and participant.selectedBallNumber is not null
          and upper(participant.match.status) in ('READY_REVEAL', 'REVEALED', 'IN_PROGRESS', 'FINISHED')
        """)
    long countRealBallRoomsPlayed(@Param("userId") Integer userId);

    @Query("""
        select count(distinct participant.match.matchId)
        from MatchParticipantEntity participant
        where participant.id.userId = :userId
          and upper(participant.match.status) = 'FINISHED'
          and participant.match.endTime is not null
          and exists (
              select event.eventId
              from GameSessionEventEntity event
              where event.match.matchId = participant.match.matchId
                and event.eventType = 'ROUND_SUMMARY'
          )
        """)
    long countRealBattlesPlayed(@Param("userId") Integer userId);

    @Query("""
        select count(distinct participant.match.matchId)
        from MatchParticipantEntity participant
        where participant.id.userId = :userId
          and upper(participant.match.status) = 'FINISHED'
          and participant.match.endTime is not null
          and (
              participant.match.winner.userId = :userId
              or (
                  participant.match.winner is null
                  and participant.alive = true
                  and not exists (
                      select other.id.userId
                      from MatchParticipantEntity other
                      where other.match.matchId = participant.match.matchId
                        and other.id.userId <> participant.id.userId
                        and other.alive = true
                  )
              )
          )
          and exists (
              select event.eventId
              from GameSessionEventEntity event
              where event.match.matchId = participant.match.matchId
                and event.eventType = 'ROUND_SUMMARY'
          )
        """)
    long countRealBattlesWon(@Param("userId") Integer userId);

    @Query("""
        select max(participant.multiplierWon)
        from MatchParticipantEntity participant
        where participant.id.userId = :userId
          and participant.selectedBallNumber is not null
          and upper(participant.match.status) in ('READY_REVEAL', 'REVEALED', 'IN_PROGRESS', 'FINISHED')
        """)
    BigDecimal findBestRealMultiplier(@Param("userId") Integer userId);

    @Query("""
        select avg(participant.multiplierWon)
        from MatchParticipantEntity participant
        where participant.id.userId = :userId
          and participant.selectedBallNumber is not null
          and upper(participant.match.status) in ('READY_REVEAL', 'REVEALED', 'IN_PROGRESS', 'FINISHED')
        """)
    Double findAverageRealMultiplier(@Param("userId") Integer userId);
}
