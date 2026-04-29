package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.UserBadgeEntity;
import com.example.fichestu.persistence.entity.UserBadgeId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBadgeRepository extends JpaRepository<UserBadgeEntity, UserBadgeId> {
    List<UserBadgeEntity> findByIdUserId(Integer userId);
}
