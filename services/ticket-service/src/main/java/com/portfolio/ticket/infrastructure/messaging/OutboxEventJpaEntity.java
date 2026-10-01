package com.portfolio.ticket.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event", schema = "ticket")
class OutboxEventJpaEntity {

    @Id private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEventJpaEntity() {}

    OutboxEventJpaEntity(
            UUID id,
            String eventType,
            String routingKey,
            UUID tenantId,
            UUID subjectId,
            JsonNode payload,
            Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.tenantId = tenantId;
        this.subjectId = subjectId;
        this.payload = payload;
        status = "PENDING";
        attemptCount = 0;
        nextAttemptAt = createdAt;
        this.createdAt = createdAt;
    }

    UUID id() {
        return id;
    }

    String eventType() {
        return eventType;
    }

    String routingKey() {
        return routingKey;
    }

    UUID tenantId() {
        return tenantId;
    }

    JsonNode payload() {
        return payload;
    }

    int attemptCount() {
        return attemptCount;
    }
}
