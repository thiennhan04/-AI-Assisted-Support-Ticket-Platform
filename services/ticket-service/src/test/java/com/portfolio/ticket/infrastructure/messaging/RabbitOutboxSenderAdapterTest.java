package com.portfolio.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class RabbitOutboxSenderAdapterTest {

    private static final OutboxProperties PROPERTIES =
            new OutboxProperties(
                    20, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMinutes(1));

    @Mock RabbitTemplate rabbit;

    @Test
    void acceptsPositiveBrokerConfirmation() {
        completePublishWith(true, null);

        assertThatCode(() -> new RabbitOutboxSenderAdapter(rabbit, PROPERTIES).send(event()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativeBrokerConfirmation() {
        completePublishWith(false, "broker rejected publish");

        assertThatThrownBy(() -> new RabbitOutboxSenderAdapter(rabbit, PROPERTIES).send(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("broker rejected publish");
    }

    private void completePublishWith(boolean acknowledged, String reason) {
        doAnswer(
                        invocation -> {
                            var confirmation = invocation.getArgument(4, CorrelationData.class);
                            confirmation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(acknowledged, reason));
                            return null;
                        })
                .when(rabbit)
                .convertAndSend(
                        anyString(),
                        anyString(),
                        any(),
                        any(MessagePostProcessor.class),
                        any(CorrelationData.class));
    }

    private JpaTicketOutboxAdapter.PendingOutboxEvent event() {
        return new JpaTicketOutboxAdapter.PendingOutboxEvent(
                UUID.randomUUID(),
                "ticket.created.v1",
                "ticket.created.v1",
                UUID.randomUUID(),
                "{}",
                1);
    }
}
