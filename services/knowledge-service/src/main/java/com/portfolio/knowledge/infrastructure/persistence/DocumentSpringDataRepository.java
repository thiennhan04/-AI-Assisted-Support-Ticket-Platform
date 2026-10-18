package com.portfolio.knowledge.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface DocumentSpringDataRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentJpaEntity d where d.tenantId = :tenantId and d.id = :id")
    Optional<DocumentJpaEntity> findForPreparation(UUID tenantId, UUID id);

    Optional<DocumentJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<DocumentJpaEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
