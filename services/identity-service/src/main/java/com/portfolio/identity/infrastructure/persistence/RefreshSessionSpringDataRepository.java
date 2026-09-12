package com.portfolio.identity.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefreshSessionSpringDataRepository extends JpaRepository<RefreshSessionJpaEntity, UUID> {}
