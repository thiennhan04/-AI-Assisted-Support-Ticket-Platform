package com.portfolio.ticket.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TicketCommentSpringDataRepository extends JpaRepository<TicketCommentJpaEntity, UUID> {

    List<TicketCommentJpaEntity> findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
            UUID tenantId, UUID ticketId);

    List<TicketCommentJpaEntity>
            findAllByTenantIdAndTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(
                    UUID tenantId, UUID ticketId);
}
