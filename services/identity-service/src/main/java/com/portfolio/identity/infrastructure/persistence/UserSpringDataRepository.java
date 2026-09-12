package com.portfolio.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface UserSpringDataRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByTenantIdAndEmail(UUID tenantId, String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
