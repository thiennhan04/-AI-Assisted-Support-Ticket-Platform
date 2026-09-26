package com.portfolio.ticket.application;

import java.io.Serial;

public final class TicketForbiddenException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public TicketForbiddenException() {
        super("Caller is not allowed to perform this ticket operation");
    }
}
