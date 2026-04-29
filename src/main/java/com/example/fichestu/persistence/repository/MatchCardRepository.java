package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.MatchCardEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchCardRepository extends JpaRepository<MatchCardEntity, Integer> {
    List<MatchCardEntity> findByMatchMatchId(Integer matchId);

    long countByMatchMatchId(Integer matchId);

    long countByMatchMatchIdAndCardType(Integer matchId, String cardType);

    List<MatchCardEntity> findByMatchMatchIdAndRoundNumber(Integer matchId, Integer roundNumber);

    Optional<MatchCardEntity> findByMatchMatchIdAndOwnerUserIdAndRoundNumber(
        Integer matchId,
        Integer ownerId,
        Integer roundNumber
    );

    long countByMatchMatchIdAndRoundNumber(Integer matchId, Integer roundNumber);
}
