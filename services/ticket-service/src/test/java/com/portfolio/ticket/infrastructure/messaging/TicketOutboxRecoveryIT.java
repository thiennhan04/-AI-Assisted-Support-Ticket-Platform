package com.portfolio.ticket.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.Ticket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class TicketOutboxRecoveryIT {

    private static final Instant CREATED_AT = Instant.parse("2026-09-30T08:00:00Z");
    private static final OutboxProperties PROPERTIES =
            new OutboxProperties(
                    20, Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMinutes(1));

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(
                            DockerImageName.parse("pgvector/pgvector:0.8.1-pg16")
                                    .asCompatibleSubstituteFor("postgres"))
                    .withDatabaseName("ticket_outbox_test")
                    .withUsername("ticket_test")
                    .withPassword("ticket_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("ticket.outbox.enabled", () -> "false");
    }

    @Autowired JpaTicketOutboxAdapter outbox;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM ticket.outbox_event");
    }

    @Test
    @DisplayName("A failed publish stays retryable and a new publisher can finish it")
    void retriesEventAfterPublisherRestart() {
        outbox.appendTicketCreated(ticket());

        var failedPublisher =
                publisherAt(
                        CREATED_AT,
                        event -> {
                            throw new IllegalStateException("simulated broker outage");
                        });
        failedPublisher.publishPending();

        assertThat(storedStatus()).isEqualTo("PENDING");
        assertThat(storedAttemptCount()).isEqualTo(1);
        assertThat(storedLastError()).contains("simulated broker outage");

        var restartedPublisher = publisherAt(CREATED_AT.plusSeconds(2), event -> {});
        restartedPublisher.publishPending();

        assertThat(storedStatus()).isEqualTo("PUBLISHED");
        assertThat(storedAttemptCount()).isEqualTo(2);
    }

    private TicketOutboxPublisher publisherAt(
            Instant now, TicketOutboxPublisher.OutboxEventSender sender) {
        return new TicketOutboxPublisher(
                outbox, sender, PROPERTIES, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Ticket ticket() {
        return Ticket.open(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SUP-00000001",
                UUID.randomUUID(),
                "Cannot sign in",
                "The customer cannot sign in to the account",
                Priority.MEDIUM,
                CREATED_AT);
    }

    private String storedStatus() {
        return jdbc.queryForObject("SELECT status FROM ticket.outbox_event", String.class);
    }

    private int storedAttemptCount() {
        return jdbc.queryForObject("SELECT attempt_count FROM ticket.outbox_event", Integer.class);
    }

    private String storedLastError() {
        return jdbc.queryForObject("SELECT last_error FROM ticket.outbox_event", String.class);
    }
}
