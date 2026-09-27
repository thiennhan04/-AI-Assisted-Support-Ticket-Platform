package com.portfolio.ticket.application;

public final class TicketCommandException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        IDEMPOTENCY_KEY_REUSED,
        PRECONDITION_REQUIRED,
        TICKET_VERSION_CONFLICT
    }

    private final Reason reason;
    private final Long currentVersion;

    private TicketCommandException(Reason reason, String message, Long currentVersion) {
        super(message);
        this.reason = reason;
        this.currentVersion = currentVersion;
    }

    public static TicketCommandException idempotencyKeyReused() {
        return new TicketCommandException(
                Reason.IDEMPOTENCY_KEY_REUSED,
                "Idempotency-Key was already used with a different request",
                null);
    }

    public static TicketCommandException preconditionRequired() {
        return new TicketCommandException(
                Reason.PRECONDITION_REQUIRED, "If-Match header is required", null);
    }

    public static TicketCommandException versionConflict(long currentVersion) {
        return new TicketCommandException(
                Reason.TICKET_VERSION_CONFLICT,
                "Ticket was changed by another request; reload it and try again",
                currentVersion);
    }

    public Reason reason() {
        return reason;
    }

    public Long currentVersion() {
        return currentVersion;
    }
}
