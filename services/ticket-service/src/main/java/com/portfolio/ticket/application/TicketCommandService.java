package com.portfolio.ticket.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketCommandService {

    private final TicketRepository ticketRepository;
    private final TicketCreationIdempotencyRepository idempotencyRepository;
    private final TicketPolicy policy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TicketCommandService(
            TicketRepository ticketRepository,
            TicketCreationIdempotencyRepository idempotencyRepository,
            TicketPolicy policy,
            ObjectMapper objectMapper) {
        this.ticketRepository = ticketRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.policy = policy;
        this.objectMapper = objectMapper;
        this.clock = Clock.systemUTC();
    }

    @Transactional
    public Ticket create(
            AuthenticatedPrincipal principal, UUID idempotencyKey, CreateTicketCommand command) {
        var now = clock.instant();
        var requestHash = hash(command);

        idempotencyRepository.lock(principal.tenantId(), principal.userId(), idempotencyKey);
        var previousRequest =
                idempotencyRepository.findActive(
                        principal.tenantId(), principal.userId(), idempotencyKey, now);
        if (previousRequest.isPresent()) {
            if (!previousRequest.get().requestHash().equals(requestHash)) {
                throw TicketCommandException.idempotencyKeyReused();
            }
            return ticketRepository
                    .findByTenantIdAndId(principal.tenantId(), previousRequest.get().ticketId())
                    .orElseThrow(TicketNotFoundException::new);
        }

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
        var created = ticketRepository.create(ticket);
        idempotencyRepository.save(
                principal.tenantId(),
                principal.userId(),
                idempotencyKey,
                requestHash,
                created.id(),
                now,
                now.plus(Duration.ofHours(24)));
        return created;
    }

    @Transactional
    public Ticket update(
            AuthenticatedPrincipal principal,
            UUID ticketId,
            long expectedVersion,
            UpdateTicketCommand command) {
        var ticket = findVisibleTicket(principal, ticketId);
        if (ticket.version() != expectedVersion) {
            throw TicketCommandException.versionConflict(ticket.version());
        }
        var now = clock.instant();

        applyContentChanges(principal, ticket, command, now);
        applyManagementChanges(principal, ticket, command, now);
        applyStatusChange(principal, ticket, command, now);
        return ticketRepository.save(ticket);
    }

    private String hash(CreateTicketCommand command) {
        try {
            var bytes = objectMapper.writeValueAsBytes(command);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash create-ticket request", exception);
        }
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
