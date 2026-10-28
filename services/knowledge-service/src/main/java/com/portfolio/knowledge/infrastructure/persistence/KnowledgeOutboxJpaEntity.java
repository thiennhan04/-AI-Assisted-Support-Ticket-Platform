package com.portfolio.knowledge.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.contracts.EventEnvelope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event", schema = "knowledge")
class KnowledgeOutboxJpaEntity {
    @Id UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID subjectId;

    @Column(nullable = false)
    String eventType;

    @Column(nullable = false)
    String routingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    JsonNode payload;

    @Column(nullable = false)
    String status;

    @Column(nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    Instant nextAttemptAt;

    @Column(nullable = false)
    int attemptCount;

    Instant publishedAt;

    protected KnowledgeOutboxJpaEntity() {}

    static KnowledgeOutboxJpaEntity event(
            String type,
            UUID tenantId,
            UUID documentId,
            Object data,
            Instant now,
            ObjectMapper mapper) {
        var eventId = UUID.randomUUID();
        var envelope =
                new EventEnvelope<>(
                        eventId,
                        type,
                        now,
                        "knowledge-service",
                        eventId.toString(),
                        null,
                        tenantId,
                        documentId,
                        1,
                        data);
        var entity = new KnowledgeOutboxJpaEntity();
        entity.id = eventId;
        entity.tenantId = tenantId;
        entity.subjectId = documentId;
        entity.eventType = type;
        entity.routingKey = type;
        entity.payload = mapper.valueToTree(envelope);
        entity.status = "PENDING";
        entity.createdAt = now;
        entity.nextAttemptAt = now;
        return entity;
    }
}
