package com.portafolio.banca.repository;

import com.portafolio.banca.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByUserIdAndKey(Long userId, String key);

    @Modifying
    @Query("delete from IdempotencyKey k where k.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
