package com.portfolio.ticket.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TicketAuditSpringDataRepository extends JpaRepository<TicketAuditJpaEntity, UUID> {}
