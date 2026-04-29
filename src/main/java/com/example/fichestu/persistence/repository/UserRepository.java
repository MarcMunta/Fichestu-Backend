package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Integer> {
	Optional<UserEntity> findByEmail(String email);

	Optional<UserEntity> findByUsername(String username);

	boolean existsByEmail(String email);

	boolean existsByUsername(String username);

	@Query(value = "select * from users where user_id = :userId for update", nativeQuery = true)
	Optional<UserEntity> findByUserIdForUpdate(@Param("userId") Integer userId);
}
