package com.portfolio.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@IdClass(IdempotencyRecordJpaEntity.Key.class)
@Table(name = "idempotency_record", schema = "ticket")
class IdempotencyRecordJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Id
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String requestHash;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecordJpaEntity() {}

    IdempotencyRecordJpaEntity(
            UUID tenantId,
            UUID requesterId,
            UUID idempotencyKey,
            String requestHash,
            UUID ticketId,
            Instant createdAt,
            Instant expiresAt) {
        this.tenantId = tenantId;
        this.requesterId = requesterId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.ticketId = ticketId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    String requestHash() {
        return requestHash;
    }

    UUID ticketId() {
        return ticketId;
    }

    public static final class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private UUID tenantId;
        private UUID requesterId;
        private UUID idempotencyKey;

        public Key() {}

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(tenantId, key.tenantId)
                    && Objects.equals(requesterId, key.requesterId)
                    && Objects.equals(idempotencyKey, key.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, requesterId, idempotencyKey);
        }
    }
}
