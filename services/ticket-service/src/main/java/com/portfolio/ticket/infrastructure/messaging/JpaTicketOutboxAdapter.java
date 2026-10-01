package com.portfolio.ticket.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.contracts.EventEnvelope;
import com.portfolio.contracts.TicketCreatedV1;
import com.portfolio.ticket.application.TicketEventOutbox;
import com.portfolio.ticket.domain.Ticket;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaTicketOutboxAdapter implements TicketEventOutbox {

    static final String TICKET_CREATED = "ticket.created.v1";
    private static final String PRODUCER = "ticket-service";

    private final OutboxEventSpringDataRepository events;
    private final ObjectMapper objectMapper;

    JpaTicketOutboxAdapter(OutboxEventSpringDataRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Override
    public void appendTicketCreated(Ticket ticket) {
        var eventId = UUID.randomUUID();
        var data =
                new TicketCreatedV1(
                        ticket.id(),
                        ticket.number(),
                        ticket.requesterId(),
                        ticket.subject(),
                        ticket.description(),
                        ticket.createdAt(),
                        ticket.contentVersion());
        var envelope =
                new EventEnvelope<>(
                        eventId,
                        TICKET_CREATED,
                        ticket.createdAt(),
                        PRODUCER,
                        eventId.toString(),
                        null,
                        ticket.tenantId(),
                        ticket.id(),
                        1,
                        data);

        events.save(
                new OutboxEventJpaEntity(
                        eventId,
                        TICKET_CREATED,
                        TICKET_CREATED,
                        ticket.tenantId(),
                        ticket.id(),
                        objectMapper.valueToTree(envelope),
                        ticket.createdAt()));
    }

    List<PendingOutboxEvent> claimBatch(Instant now, Instant staleBefore, int batchSize) {
        return events.claimBatch(now, staleBefore, batchSize).stream()
                .map(
                        event ->
                                new PendingOutboxEvent(
                                        event.id(),
                                        event.eventType(),
                                        event.routingKey(),
                                        event.tenantId(),
                                        event.payload().toString(),
                                        event.attemptCount()))
                .toList();
    }

    void markPublished(UUID eventId, Instant publishedAt) {
        events.markPublished(eventId, publishedAt);
    }

    void reschedule(UUID eventId, Instant nextAttemptAt, String error) {
        events.reschedule(eventId, nextAttemptAt, error);
    }

    record PendingOutboxEvent(
            UUID id,
            String eventType,
            String routingKey,
            UUID tenantId,
            String payload,
            int attemptCount) {}
}
