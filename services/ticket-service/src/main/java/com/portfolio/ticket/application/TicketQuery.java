package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.Category;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public record TicketQuery(
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
        String sort) {}
