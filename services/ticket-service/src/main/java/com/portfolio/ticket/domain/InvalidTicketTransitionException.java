package com.portfolio.ticket.domain;

import java.io.Serial;

public final class InvalidTicketTransitionException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public InvalidTicketTransitionException(TicketStatus from, TicketStatus to) {
        super("Ticket cannot transition from " + from + " to " + to);
    }
}
