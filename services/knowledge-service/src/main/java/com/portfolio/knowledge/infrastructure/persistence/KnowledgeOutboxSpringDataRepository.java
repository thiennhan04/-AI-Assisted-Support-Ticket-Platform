package com.portfolio.knowledge.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeOutboxSpringDataRepository
        extends JpaRepository<KnowledgeOutboxJpaEntity, UUID> {}
