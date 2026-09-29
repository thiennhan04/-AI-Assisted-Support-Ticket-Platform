package com.portfolio.ticket.api.dto;

import com.portfolio.ticket.domain.TicketComment;
import java.time.Instant;
import java.util.UUID;

public record TicketCommentResponse(
        UUID id, UUID ticketId, UUID authorId, String body, boolean internal, Instant createdAt) {

    public static TicketCommentResponse from(TicketComment comment) {
        return new TicketCommentResponse(
                comment.id(),
                comment.ticketId(),
                comment.authorId(),
                comment.body(),
                comment.internal(),
                comment.createdAt());
    }
}
