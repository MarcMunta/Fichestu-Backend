package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.MatchCardEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchCardRepository extends JpaRepository<MatchCardEntity, Integer> {
    List<MatchCardEntity> findByMatchMatchId(Integer matchId);

    List<MatchCardEntity> findByMatchMatchIdAndRoundNumber(Integer matchId, Integer roundNumber);

    List<MatchCardEntity> findByMatchMatchIdAndRoundNumberAndOwnerUserId(Integer matchId, Integer roundNumber, Integer ownerId);

    long countByMatchMatchId(Integer matchId);

    long countByMatchMatchIdAndCardType(Integer matchId, String cardType);

    long countByMatchMatchIdAndRoundNumber(Integer matchId, Integer roundNumber);
}
