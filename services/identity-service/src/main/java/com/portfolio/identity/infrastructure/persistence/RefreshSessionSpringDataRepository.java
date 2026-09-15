package com.portfolio.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshSessionSpringDataRepository extends JpaRepository<RefreshSessionJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshSessionJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            """
            update RefreshSessionJpaEntity session
               set session.revokedAt = :revokedAt
             where session.tenantId = :tenantId
               and session.familyId = :familyId
               and session.revokedAt is null
            """)
    int revokeFamily(
            @Param("tenantId") UUID tenantId,
            @Param("familyId") UUID familyId,
            @Param("revokedAt") Instant revokedAt);
}
