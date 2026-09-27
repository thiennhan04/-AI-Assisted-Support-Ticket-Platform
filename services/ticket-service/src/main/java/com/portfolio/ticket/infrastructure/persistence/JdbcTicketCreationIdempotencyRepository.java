package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.application.TicketCreationIdempotencyRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTicketCreationIdempotencyRepository implements TicketCreationIdempotencyRepository {

    private final JdbcTemplate jdbc;

    JdbcTicketCreationIdempotencyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock(UUID tenantId, UUID requesterId, UUID idempotencyKey) {
        // The transaction-scoped lock serializes concurrent retries for the same caller and key.
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                resultSet -> {
                    resultSet.next();
                    return null;
                },
                tenantId + ":" + requesterId + ":" + idempotencyKey);
    }

    @Override
    public Optional<StoredCreateRequest> findActive(
            UUID tenantId, UUID requesterId, UUID idempotencyKey, Instant now) {
        var records =
                jdbc.query(
                        """
                        SELECT request_hash, ticket_id
                        FROM ticket.idempotency_record
                        WHERE tenant_id = ? AND requester_id = ? AND idempotency_key = ?
                          AND expires_at > ?
                        """,
                        (result, row) ->
                                new StoredCreateRequest(
                                        result.getString("request_hash"),
                                        result.getObject("ticket_id", UUID.class)),
                        tenantId,
                        requesterId,
                        idempotencyKey,
                        Timestamp.from(now));
        return records.stream().findFirst();
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
        jdbc.update(
                """
                INSERT INTO ticket.idempotency_record
                    (tenant_id, requester_id, idempotency_key, request_hash, ticket_id,
                     created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenant_id, requester_id, idempotency_key) DO UPDATE SET
                    request_hash = EXCLUDED.request_hash,
                    ticket_id = EXCLUDED.ticket_id,
                    created_at = EXCLUDED.created_at,
                    expires_at = EXCLUDED.expires_at
                WHERE ticket.idempotency_record.expires_at <= EXCLUDED.created_at
                """,
                tenantId,
                requesterId,
                idempotencyKey,
                requestHash,
                ticketId,
                Timestamp.from(createdAt),
                Timestamp.from(expiresAt));
    }
}
