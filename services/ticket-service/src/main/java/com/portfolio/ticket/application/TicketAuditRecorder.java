package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.Category;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketAuditAction;
import com.portfolio.ticket.domain.TicketHistoryRepository;
import com.portfolio.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TicketAuditRecorder {

    private final TicketHistoryRepository history;

    TicketAuditRecorder(TicketHistoryRepository history) {
        this.history = history;
    }

    Snapshot snapshot(Ticket ticket) {
        return new Snapshot(
                ticket.subject(),
                ticket.description(),
                ticket.category(),
                ticket.priority(),
                ticket.assigneeId(),
                ticket.status());
    }

    void ticketCreated(AuthenticatedPrincipal principal, Ticket ticket, Instant now) {
        append(principal, ticket, TicketAuditAction.TICKET_CREATED, null, ticket.number(), now);
    }

    void ticketChanged(
            AuthenticatedPrincipal principal, Snapshot before, Ticket after, Instant now) {
        if (!before.subject().equals(after.subject())
                || !before.description().equals(after.description())) {
            append(principal, after, TicketAuditAction.CONTENT_UPDATED, null, null, now);
        }
        appendIfChanged(
                principal,
                after,
                TicketAuditAction.PRIORITY_CHANGED,
                before.priority(),
                after.priority(),
                now);
        appendIfChanged(
                principal,
                after,
                TicketAuditAction.CATEGORY_CHANGED,
                before.category(),
                after.category(),
                now);
        appendIfChanged(
                principal,
                after,
                TicketAuditAction.ASSIGNEE_CHANGED,
                before.assigneeId(),
                after.assigneeId(),
                now);
        appendIfChanged(
                principal,
                after,
                TicketAuditAction.STATUS_CHANGED,
                before.status(),
                after.status(),
                now);
    }

    void commentAdded(
            AuthenticatedPrincipal principal, Ticket ticket, UUID commentId, Instant now) {
        append(principal, ticket, TicketAuditAction.COMMENT_ADDED, null, commentId.toString(), now);
    }

    private void appendIfChanged(
            AuthenticatedPrincipal principal,
            Ticket ticket,
            TicketAuditAction action,
            Object oldValue,
            Object newValue,
            Instant now) {
        if (!Objects.equals(oldValue, newValue)) {
            append(
                    principal,
                    ticket,
                    action,
                    oldValue == null ? null : oldValue.toString(),
                    newValue == null ? null : newValue.toString(),
                    now);
        }
    }

    private void append(
            AuthenticatedPrincipal principal,
            Ticket ticket,
            TicketAuditAction action,
            String oldValue,
            String newValue,
            Instant now) {
        history.appendAudit(
                ticket.tenantId(),
                ticket.id(),
                principal.userId(),
                action,
                oldValue,
                newValue,
                now);
    }

    record Snapshot(
            String subject,
            String description,
            Category category,
            Priority priority,
            UUID assigneeId,
            TicketStatus status) {}
}
