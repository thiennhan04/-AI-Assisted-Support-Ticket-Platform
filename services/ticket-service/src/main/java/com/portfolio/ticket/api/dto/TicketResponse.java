package com.portfolio.ticket.api.dto;

import com.portfolio.ticket.domain.Category;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String number,
        UUID requesterId,
        UUID assigneeId,
        String subject,
        String description,
        Category category,
        Priority priority,
        TicketStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.id(),
                ticket.number(),
                ticket.requesterId(),
                ticket.assigneeId(),
                ticket.subject(),
                ticket.description(),
                ticket.category(),
                ticket.priority(),
                ticket.status(),
                ticket.version(),
                ticket.createdAt(),
                ticket.updatedAt());
    }
}
