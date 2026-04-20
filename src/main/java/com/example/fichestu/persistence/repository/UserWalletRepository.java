package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.UserWalletEntity;
import com.example.fichestu.persistence.entity.UserWalletId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWalletRepository extends JpaRepository<UserWalletEntity, UserWalletId> {
    List<UserWalletEntity> findByIdUserId(Integer userId);
}
