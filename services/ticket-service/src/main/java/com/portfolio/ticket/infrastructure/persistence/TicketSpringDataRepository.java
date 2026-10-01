package com.portfolio.ticket.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface TicketSpringDataRepository
        extends JpaRepository<TicketJpaEntity, UUID>, JpaSpecificationExecutor<TicketJpaEntity> {

    @Query(value = "SELECT nextval('ticket.ticket_number_seq')", nativeQuery = true)
    long nextNumberSequenceValue();

    Optional<TicketJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);
}
