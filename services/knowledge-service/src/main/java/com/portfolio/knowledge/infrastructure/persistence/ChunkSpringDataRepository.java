package com.portfolio.knowledge.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ChunkSpringDataRepository extends JpaRepository<ChunkJpaEntity, UUID> {}
