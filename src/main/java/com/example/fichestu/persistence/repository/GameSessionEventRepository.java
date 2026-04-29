package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.GameSessionEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionEventRepository extends JpaRepository<GameSessionEventEntity, Integer> {
    List<GameSessionEventEntity> findTop24ByMatchMatchIdOrderByEventIdDesc(Integer matchId);

    long countByMatchMatchIdAndEventType(Integer matchId, String eventType);
}
