package com.portfolio.ticket.domain;

import java.time.Instant;
import java.util.UUID;

public record TicketSearchCriteria(
        UUID tenantId,
        UUID visibleRequesterId,
        TicketStatus status,
        Priority priority,
        Category category,
        UUID assigneeId,
        UUID requesterId,
        Instant createdFrom,
        Instant createdTo,
        String query,
        int page,
        int size,
        String sortProperty,
        boolean ascending) {}
