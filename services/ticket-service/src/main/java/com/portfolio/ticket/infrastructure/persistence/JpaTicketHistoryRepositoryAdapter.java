package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.domain.TicketAuditAction;
import com.portfolio.ticket.domain.TicketComment;
import com.portfolio.ticket.domain.TicketHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketHistoryRepositoryAdapter implements TicketHistoryRepository {

    private final TicketCommentSpringDataRepository comments;
    private final TicketAuditSpringDataRepository audits;

    JpaTicketHistoryRepositoryAdapter(
            TicketCommentSpringDataRepository comments, TicketAuditSpringDataRepository audits) {
        this.comments = comments;
        this.audits = audits;
    }

    @Override
    public TicketComment addComment(TicketComment comment) {
        return comments.save(new TicketCommentJpaEntity(comment)).toDomain();
    }

    @Override
    public List<TicketComment> findComments(UUID tenantId, UUID ticketId, boolean includeInternal) {
        var result =
                includeInternal
                        ? comments.findAllByTenantIdAndTicketIdOrderByCreatedAtAscIdAsc(
                                tenantId, ticketId)
                        : comments
                                .findAllByTenantIdAndTicketIdAndInternalFalseOrderByCreatedAtAscIdAsc(
                                        tenantId, ticketId);
        return result.stream().map(TicketCommentJpaEntity::toDomain).toList();
    }

    @Override
    public void appendAudit(
            UUID tenantId,
            UUID ticketId,
            UUID actorId,
            TicketAuditAction action,
            String oldValue,
            String newValue,
            Instant occurredAt) {
        audits.save(
                new TicketAuditJpaEntity(
                        UUID.randomUUID(),
                        tenantId,
                        ticketId,
                        actorId,
                        action,
                        oldValue,
                        newValue,
                        occurredAt));
    }
}
