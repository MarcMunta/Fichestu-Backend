package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.MatchParticipantEntity;
import com.example.fichestu.persistence.entity.MatchParticipantId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipantEntity, MatchParticipantId> {
    List<MatchParticipantEntity> findByIdMatchId(Integer matchId);

    List<MatchParticipantEntity> findByIdUserId(Integer userId);

    long countByIdMatchId(Integer matchId);

    long countByIdMatchIdAndSelectedBallNumberIsNotNull(Integer matchId);

    boolean existsByIdMatchIdAndIdUserId(Integer matchId, Integer userId);
}
