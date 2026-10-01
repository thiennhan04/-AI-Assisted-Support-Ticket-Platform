package com.portfolio.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TicketCreatedV1(
        UUID ticketId,
        String ticketNumber,
        UUID requesterId,
        String subject,
        String description,
        Instant createdAt,
        long contentVersion) {

    public TicketCreatedV1 {
        Objects.requireNonNull(ticketId);
        Objects.requireNonNull(ticketNumber);
        Objects.requireNonNull(requesterId);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(description);
        Objects.requireNonNull(createdAt);
    }
}
