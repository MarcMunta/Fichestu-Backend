package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
	Optional<UserEntity> findByEmail(String email);

	Optional<UserEntity> findByUsername(String username);

	boolean existsByEmail(String email);

	boolean existsByUsername(String username);
}
