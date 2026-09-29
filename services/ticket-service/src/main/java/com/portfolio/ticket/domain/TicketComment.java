package com.portfolio.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TicketComment(
        UUID id,
        UUID tenantId,
        UUID ticketId,
        UUID authorId,
        String body,
        boolean internal,
        Instant createdAt) {

    public TicketComment {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(ticketId);
        Objects.requireNonNull(authorId);
        Objects.requireNonNull(createdAt);
        if (body == null || body.isBlank() || body.length() > 10_000) {
            throw new IllegalArgumentException("comment body must contain 1 to 10000 characters");
        }
        body = body.trim();
    }
}
