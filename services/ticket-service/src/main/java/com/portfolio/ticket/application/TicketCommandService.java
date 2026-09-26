package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketCommandService {

    private final TicketRepository ticketRepository;
    private final TicketPolicy policy;
    private final Clock clock;

    public TicketCommandService(TicketRepository ticketRepository, TicketPolicy policy) {
        this.ticketRepository = ticketRepository;
        this.policy = policy;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public Ticket create(AuthenticatedPrincipal principal, CreateTicketCommand command) {
        var now = clock.instant();
        var ticket =
                Ticket.open(
                        UUID.randomUUID(),
                        principal.tenantId(),
                        ticketRepository.nextNumber(),
                        principal.userId(),
                        command.subject(),
                        command.description(),
                        command.priority() == null ? Priority.MEDIUM : command.priority(),
                        now);
        return ticketRepository.create(ticket);
    }

    @Transactional
    public Ticket update(
            AuthenticatedPrincipal principal, UUID ticketId, UpdateTicketCommand command) {
        var ticket = findVisibleTicket(principal, ticketId);
        var now = clock.instant();

        applyContentChanges(principal, ticket, command, now);
        applyManagementChanges(principal, ticket, command, now);
        applyStatusChange(principal, ticket, command, now);
        return ticketRepository.save(ticket);
    }

    private void applyContentChanges(
            AuthenticatedPrincipal principal,
            Ticket ticket,
            UpdateTicketCommand command,
            Instant now) {
        if (command.subject() != null || command.description() != null) {
            policy.requireCanEditContent(principal, ticket);
            ticket.updateContent(
                    command.subject() == null ? ticket.subject() : command.subject(),
                    command.description() == null ? ticket.description() : command.description(),
                    now);
        }
    }

    private void applyManagementChanges(
            AuthenticatedPrincipal principal,
            Ticket ticket,
            UpdateTicketCommand command,
            Instant now) {
        if (!hasManagementChanges(command)) {
            return;
        }
        policy.requireCanManage(principal, ticket);
        if (command.priority() != null) {
            ticket.changePriority(command.priority(), now);
        }
        if (command.category() != null) {
            ticket.changeCategory(command.category(), now);
        }
        if (command.assigneeId() != null) {
            ticket.assign(command.assigneeId(), now);
        }
    }

    private void applyStatusChange(
            AuthenticatedPrincipal principal,
            Ticket ticket,
            UpdateTicketCommand command,
            Instant now) {
        if (command.status() != null) {
            policy.requireCanTransition(principal, ticket, command.status());
            ticket.transitionTo(command.status(), now);
        }
    }

    private boolean hasManagementChanges(UpdateTicketCommand command) {
        return command.priority() != null
                || command.category() != null
                || command.assigneeId() != null;
    }

    private Ticket findVisibleTicket(AuthenticatedPrincipal principal, UUID ticketId) {
        var ticket =
                ticketRepository
                        .findByTenantIdAndId(principal.tenantId(), ticketId)
                        .orElseThrow(TicketNotFoundException::new);
        policy.requireCanView(principal, ticket);
        return ticket;
    }
}
