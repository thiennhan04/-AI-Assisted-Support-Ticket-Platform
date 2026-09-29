package com.portfolio.ticket.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TicketHistoryRepository {

    TicketComment addComment(TicketComment comment);

    List<TicketComment> findComments(UUID tenantId, UUID ticketId, boolean includeInternal);

    void appendAudit(
            UUID tenantId,
            UUID ticketId,
            UUID actorId,
            TicketAuditAction action,
            String oldValue,
            String newValue,
            Instant occurredAt);
}
