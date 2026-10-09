package com.portfolio.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface DocumentSpringDataRepository extends JpaRepository<DocumentJpaEntity, UUID> {
    Optional<DocumentJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<DocumentJpaEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
