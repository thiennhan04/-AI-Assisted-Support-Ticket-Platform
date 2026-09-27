package com.portfolio.ticket.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Stores the result of a create request so a client retry does not create a second ticket. */
public interface TicketCreationIdempotencyRepository {

    void lock(UUID tenantId, UUID requesterId, UUID idempotencyKey);

    Optional<StoredCreateRequest> findActive(
            UUID tenantId, UUID requesterId, UUID idempotencyKey, Instant now);

    void save(
            UUID tenantId,
            UUID requesterId,
            UUID idempotencyKey,
            String requestHash,
            UUID ticketId,
            Instant createdAt,
            Instant expiresAt);

    record StoredCreateRequest(String requestHash, UUID ticketId) {}
}
