package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.InvalidTicketTransitionException;
import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketStatus;
import org.springframework.stereotype.Component;

@Component
public class TicketPolicy {

    private static final String AGENT = "AGENT";
    private static final String ADMIN = "ADMIN";

    public void requireCanView(AuthenticatedPrincipal principal, Ticket ticket) {
        if (!canView(principal, ticket)) {
            throw new TicketNotFoundException();
        }
    }

    public void requireCanEditContent(AuthenticatedPrincipal principal, Ticket ticket) {
        requireCanView(principal, ticket);
        if (!canEditContent(principal, ticket)) {
            throw new TicketForbiddenException();
        }
    }

    public void requireCanManage(AuthenticatedPrincipal principal, Ticket ticket) {
        requireCanView(principal, ticket);
        if (!isSupport(principal)) {
            throw new TicketForbiddenException();
        }
    }

    public void requireCanTransition(
            AuthenticatedPrincipal principal, Ticket ticket, TicketStatus target) {
        requireCanView(principal, ticket);
        if (!ticket.canTransitionTo(target)) {
            throw new InvalidTicketTransitionException(ticket.status(), target);
        }
        if (ticket.status() == target) {
            return;
        }
        if (!canPerformTransition(principal, ticket)) {
            throw new TicketForbiddenException();
        }
    }

    public boolean isSupport(AuthenticatedPrincipal principal) {
        return principal.hasRole(AGENT) || principal.hasRole(ADMIN);
    }

    private boolean canPerformTransition(AuthenticatedPrincipal principal, Ticket ticket) {
        return switch (ticket.status()) {
            case OPEN, IN_PROGRESS -> isSupport(principal);
            case WAITING_CUSTOMER, RESOLVED ->
                    isSupport(principal) || isRequester(principal, ticket);
            case CLOSED -> principal.hasRole(ADMIN);
        };
    }

    private boolean canView(AuthenticatedPrincipal principal, Ticket ticket) {
        return isSameTenant(principal, ticket)
                && (isSupport(principal) || isRequester(principal, ticket));
    }

    private boolean canEditContent(AuthenticatedPrincipal principal, Ticket ticket) {
        return isSupport(principal)
                || (isRequester(principal, ticket) && ticket.status() == TicketStatus.OPEN);
    }

    private boolean isSameTenant(AuthenticatedPrincipal principal, Ticket ticket) {
        return principal.tenantId().equals(ticket.tenantId());
    }

    private boolean isRequester(AuthenticatedPrincipal principal, Ticket ticket) {
        return principal.userId().equals(ticket.requesterId());
    }
}
