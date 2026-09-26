package com.portfolio.ticket.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTest {

    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void startsOpenAndUpdatesContentVersionOnlyWhenContentChanges() {
        var ticket = openTicket();

        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.contentVersion()).isZero();

        ticket.updateContent("Updated subject", "Updated description", NOW.plusSeconds(1));
        assertThat(ticket.contentVersion()).isEqualTo(1);

        ticket.updateContent("Updated subject", "Updated description", NOW.plusSeconds(2));
        assertThat(ticket.contentVersion()).isEqualTo(1);
    }

    @Test
    void supportsEveryDocumentedTransition() {
        var transitions =
                List.of(
                        transition(TicketStatus.OPEN, TicketStatus.IN_PROGRESS),
                        transition(TicketStatus.OPEN, TicketStatus.RESOLVED),
                        transition(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_CUSTOMER),
                        transition(TicketStatus.WAITING_CUSTOMER, TicketStatus.IN_PROGRESS),
                        transition(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
                        transition(TicketStatus.RESOLVED, TicketStatus.CLOSED),
                        transition(TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS),
                        transition(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS));

        for (var transition : transitions) {
            var ticket = ticketIn(transition.from());
            ticket.transitionTo(transition.to(), NOW.plusSeconds(1));
            assertThat(ticket.status()).isEqualTo(transition.to());
        }
    }

    @Test
    void rejectsUndocumentedTransitions() {
        assertThatThrownBy(() -> openTicket().transitionTo(TicketStatus.CLOSED, NOW.plusSeconds(1)))
                .isInstanceOf(InvalidTicketTransitionException.class);
        assertThatThrownBy(
                        () ->
                                ticketIn(TicketStatus.WAITING_CUSTOMER)
                                        .transitionTo(TicketStatus.RESOLVED, NOW.plusSeconds(1)))
                .isInstanceOf(InvalidTicketTransitionException.class);
    }

    @Test
    void closedTicketCannotMutateUntilReopened() {
        var ticket = ticketIn(TicketStatus.CLOSED);

        assertThatThrownBy(
                        () ->
                                ticket.updateContent(
                                        "Changed subject",
                                        "Changed description",
                                        NOW.plusSeconds(1)))
                .isInstanceOf(ClosedTicketMutationException.class);

        ticket.transitionTo(TicketStatus.IN_PROGRESS, NOW.plusSeconds(2));
        ticket.updateContent("Changed subject", "Changed description", NOW.plusSeconds(3));
        assertThat(ticket.subject()).isEqualTo("Changed subject");
    }

    @Test
    void assigningOpenTicketStartsProgress() {
        var ticket = openTicket();
        var agentId = UUID.randomUUID();

        ticket.assign(agentId, NOW.plusSeconds(1));

        assertThat(ticket.assigneeId()).isEqualTo(agentId);
        assertThat(ticket.status()).isEqualTo(TicketStatus.IN_PROGRESS);
    }

    private Ticket openTicket() {
        return Ticket.open(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SUP-00000001",
                UUID.randomUUID(),
                "Cannot sign in",
                "The customer cannot sign in to the account",
                Priority.MEDIUM,
                NOW);
    }

    private Ticket ticketIn(TicketStatus status) {
        var ticket = openTicket();
        switch (status) {
            case OPEN -> {
                return ticket;
            }
            case IN_PROGRESS -> ticket.transitionTo(TicketStatus.IN_PROGRESS, NOW);
            case WAITING_CUSTOMER -> {
                ticket.transitionTo(TicketStatus.IN_PROGRESS, NOW);
                ticket.transitionTo(TicketStatus.WAITING_CUSTOMER, NOW);
            }
            case RESOLVED -> ticket.transitionTo(TicketStatus.RESOLVED, NOW);
            case CLOSED -> {
                ticket.transitionTo(TicketStatus.RESOLVED, NOW);
                ticket.transitionTo(TicketStatus.CLOSED, NOW);
            }
        }
        return ticket;
    }

    private Transition transition(TicketStatus from, TicketStatus to) {
        return new Transition(from, to);
    }

    private record Transition(TicketStatus from, TicketStatus to) {}
}
