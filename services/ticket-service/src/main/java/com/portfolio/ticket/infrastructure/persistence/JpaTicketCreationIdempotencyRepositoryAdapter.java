package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.application.TicketCreationIdempotencyRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketCreationIdempotencyRepositoryAdapter implements TicketCreationIdempotencyRepository {

    private final IdempotencyRecordSpringDataRepository records;

    JpaTicketCreationIdempotencyRepositoryAdapter(IdempotencyRecordSpringDataRepository records) {
        this.records = records;
    }

    @Override
    public void lock(UUID tenantId, UUID requesterId, UUID idempotencyKey) {
        // The transaction-scoped lock serializes concurrent retries for the same caller and key.
        records.acquireTransactionLock(tenantId + ":" + requesterId + ":" + idempotencyKey);
    }

    @Override
    public Optional<StoredCreateRequest> findActive(
            UUID tenantId, UUID requesterId, UUID idempotencyKey, Instant now) {
        return records.findByTenantIdAndRequesterIdAndIdempotencyKeyAndExpiresAtAfter(
                        tenantId, requesterId, idempotencyKey, now)
                .map(record -> new StoredCreateRequest(record.requestHash(), record.ticketId()));
    }

    @Override
    public void save(
            UUID tenantId,
            UUID requesterId,
            UUID idempotencyKey,
            String requestHash,
            UUID ticketId,
            Instant createdAt,
            Instant expiresAt) {
        records.save(
                new IdempotencyRecordJpaEntity(
                        tenantId,
                        requesterId,
                        idempotencyKey,
                        requestHash,
                        ticketId,
                        createdAt,
                        expiresAt));
    }
}
