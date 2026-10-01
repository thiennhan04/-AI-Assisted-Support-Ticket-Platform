package com.portfolio.contracts;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String producer,
        String correlationId,
        UUID causationId,
        UUID tenantId,
        UUID subjectId,
        int schemaVersion,
        T data) {

    public EventEnvelope {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(producer);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(subjectId);
        Objects.requireNonNull(data);
    }
}
