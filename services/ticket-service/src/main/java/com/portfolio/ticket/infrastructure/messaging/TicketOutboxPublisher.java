package com.portfolio.ticket.infrastructure.messaging;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticket.outbox.enabled", havingValue = "true", matchIfMissing = true)
class TicketOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketOutboxPublisher.class);

    private final JpaTicketOutboxAdapter outbox;
    private final OutboxEventSender sender;
    private final OutboxProperties properties;
    private final Clock clock;

    TicketOutboxPublisher(
            JpaTicketOutboxAdapter outbox, OutboxEventSender sender, OutboxProperties properties) {
        this(outbox, sender, properties, Clock.systemUTC());
    }

    TicketOutboxPublisher(
            JpaTicketOutboxAdapter outbox,
            OutboxEventSender sender,
            OutboxProperties properties,
            Clock clock) {
        this.outbox = outbox;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${ticket.outbox.initial-delay:1000}",
            fixedDelayString = "${ticket.outbox.poll-interval:1000}")
    void publishPending() {
        var now = clock.instant();
        var events =
                outbox.claimBatch(
                        now, now.minus(properties.claimTimeout()), properties.batchSize());

        for (var event : events) {
            publish(event);
        }
    }

    private void publish(JpaTicketOutboxAdapter.PendingOutboxEvent event) {
        try {
            sender.send(event);
            outbox.markPublished(event.id(), clock.instant());
            LOGGER.info(
                    "event=outbox_published eventId={} eventType={} tenantId={}",
                    event.id(),
                    event.eventType(),
                    event.tenantId());
        } catch (RuntimeException exception) {
            var nextAttempt = clock.instant().plus(retryDelay(event.attemptCount()));
            outbox.reschedule(event.id(), nextAttempt, failureSummary(exception));
            LOGGER.warn(
                    "event=outbox_publish_failed eventId={} eventType={} tenantId={} attempt={} errorCode=BROKER_PUBLISH_FAILED",
                    event.id(),
                    event.eventType(),
                    event.tenantId(),
                    event.attemptCount(),
                    exception);
        }
    }

    private Duration retryDelay(int attemptCount) {
        var exponent = Math.min(Math.max(attemptCount - 1, 0), 6);
        var delay = Duration.ofSeconds(1L << exponent);
        return delay.compareTo(properties.retryMaxDelay()) <= 0
                ? delay
                : properties.retryMaxDelay();
    }

    private String failureSummary(RuntimeException exception) {
        var message = exception.getMessage();
        var summary =
                exception.getClass().getSimpleName()
                        + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.substring(0, Math.min(summary.length(), 500));
    }

    interface OutboxEventSender {

        void send(JpaTicketOutboxAdapter.PendingOutboxEvent event);
    }
}
