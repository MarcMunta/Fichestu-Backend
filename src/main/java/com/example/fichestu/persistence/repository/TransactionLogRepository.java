package com.example.fichestu.persistence.repository;

import com.example.fichestu.persistence.entity.TransactionLogEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionLogRepository extends JpaRepository<TransactionLogEntity, Integer> {
    long countByUserUserIdAndType(Integer userId, String type);

    Optional<TransactionLogEntity> findTopByUserUserIdAndTypeOrderByCreatedAtDesc(Integer userId, String type);

    Optional<TransactionLogEntity> findTopByUserUserIdAndTypeAndDescriptionStartingWithOrderByCreatedAtDesc(
        Integer userId,
        String type,
        String description
    );

    boolean existsByUserUserIdAndTypeAndDescription(Integer userId, String type, String description);

    @Query("""
        select max(log.amountFiat)
        from TransactionLogEntity log
        where log.user.userId = :userId
          and log.type = :type
        """)
    java.math.BigDecimal findMaxAmountByUserAndType(@Param("userId") Integer userId, @Param("type") String type);

    @Query("""
        select avg(log.amountFiat)
        from TransactionLogEntity log
        where log.user.userId = :userId
          and log.type = :type
        """)
    Double findAverageAmountByUserAndType(@Param("userId") Integer userId, @Param("type") String type);

    List<TransactionLogEntity> findTop20ByUserUserIdOrderByCreatedAtDesc(Integer userId);
}
