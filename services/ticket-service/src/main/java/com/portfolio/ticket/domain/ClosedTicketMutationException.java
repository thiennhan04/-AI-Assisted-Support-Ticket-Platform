package com.portfolio.ticket.domain;

import java.io.Serial;

public final class ClosedTicketMutationException extends RuntimeException {

    @Serial private static final long serialVersionUID = 1L;

    public ClosedTicketMutationException() {
        super("Closed ticket is immutable until reopened");
    }
}
