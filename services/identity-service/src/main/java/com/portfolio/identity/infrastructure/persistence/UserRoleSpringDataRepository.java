package com.portfolio.identity.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRoleSpringDataRepository extends JpaRepository<UserRoleJpaEntity, UserRoleKey> {

    List<UserRoleJpaEntity> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);
}
