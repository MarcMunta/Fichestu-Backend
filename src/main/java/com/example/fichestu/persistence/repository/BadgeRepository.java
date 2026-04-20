package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.BadgeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<BadgeEntity, Integer> {
    List<BadgeEntity> findAllByOrderByBadgeIdAsc();
}
