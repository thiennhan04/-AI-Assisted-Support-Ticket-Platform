package com.portfolio.ticket.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticket.outbox.enabled", havingValue = "true", matchIfMissing = true)
class RabbitOutboxSenderAdapter implements TicketOutboxPublisher.OutboxEventSender {

    private final RabbitTemplate rabbit;
    private final OutboxProperties properties;

    RabbitOutboxSenderAdapter(RabbitTemplate rabbit, OutboxProperties properties) {
        this.rabbit = rabbit;
        this.properties = properties;
    }

    @Override
    public void send(JpaTicketOutboxAdapter.PendingOutboxEvent event) {
        var confirmation = new CorrelationData(event.id().toString());
        rabbit.convertAndSend(
                OutboxConfiguration.DOMAIN_EXCHANGE,
                event.routingKey(),
                event.payload(),
                message -> {
                    var headers = message.getMessageProperties();
                    headers.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    headers.setContentEncoding(StandardCharsets.UTF_8.name());
                    headers.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    headers.setMessageId(event.id().toString());
                    headers.setType(event.eventType());
                    return message;
                },
                confirmation);

        try {
            var result =
                    confirmation
                            .getFuture()
                            .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!result.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ rejected the event: " + result.getReason());
            }
            if (confirmation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ could not route the event");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while awaiting RabbitMQ confirmation", exception);
        } catch (java.util.concurrent.ExecutionException
                | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ confirmation was not received", exception);
        }
    }
}
