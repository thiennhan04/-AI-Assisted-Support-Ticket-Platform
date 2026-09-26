package com.portfolio.ticket.application;

import java.io.Serial;

public final class TicketNotFoundException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public TicketNotFoundException() {
        super("Ticket was not found");
    }
}
