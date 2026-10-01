package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.domain.TicketComment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_comment", schema = "ticket")
class TicketCommentJpaEntity {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketCommentJpaEntity() {}

    TicketCommentJpaEntity(TicketComment comment) {
        id = comment.id();
        tenantId = comment.tenantId();
        ticketId = comment.ticketId();
        authorId = comment.authorId();
        body = comment.body();
        internal = comment.internal();
        createdAt = comment.createdAt();
    }

    TicketComment toDomain() {
        return new TicketComment(id, tenantId, ticketId, authorId, body, internal, createdAt);
    }
}
