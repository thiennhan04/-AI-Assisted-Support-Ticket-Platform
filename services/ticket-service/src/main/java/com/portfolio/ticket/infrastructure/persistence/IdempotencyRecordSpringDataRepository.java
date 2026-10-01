package com.portfolio.ticket.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IdempotencyRecordSpringDataRepository
        extends JpaRepository<IdempotencyRecordJpaEntity, IdempotencyRecordJpaEntity.Key> {

    Optional<IdempotencyRecordJpaEntity>
            findByTenantIdAndRequesterIdAndIdempotencyKeyAndExpiresAtAfter(
                    UUID tenantId, UUID requesterId, UUID idempotencyKey, Instant now);

    @Query(
            value = "SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))",
            nativeQuery = true)
    void acquireTransactionLock(@Param("lockKey") String lockKey);
}
