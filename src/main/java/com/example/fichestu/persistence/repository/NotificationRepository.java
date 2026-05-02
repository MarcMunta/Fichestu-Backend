package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.NotificationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Integer> {
    List<NotificationEntity> findTop20ByUser_UserIdOrderByCreatedAtDesc(Integer userId);

    long countByUser_UserIdAndReadAtIsNull(Integer userId);

    void deleteByUser_UserId(Integer userId);
}
