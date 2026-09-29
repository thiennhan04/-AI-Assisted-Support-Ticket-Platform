package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.domain.TicketAuditAction;
import com.portfolio.ticket.domain.TicketComment;
import com.portfolio.ticket.domain.TicketHistoryRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTicketHistoryRepository implements TicketHistoryRepository {

    private final JdbcTemplate jdbc;

    JdbcTicketHistoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TicketComment addComment(TicketComment comment) {
        jdbc.update(
                """
                INSERT INTO ticket.ticket_comment
                    (id, tenant_id, ticket_id, author_id, body, internal, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                comment.id(),
                comment.tenantId(),
                comment.ticketId(),
                comment.authorId(),
                comment.body(),
                comment.internal(),
                Timestamp.from(comment.createdAt()));
        return comment;
    }

    @Override
    public List<TicketComment> findComments(UUID tenantId, UUID ticketId, boolean includeInternal) {
        return jdbc.query(
                """
                SELECT id, tenant_id, ticket_id, author_id, body, internal, created_at
                FROM ticket.ticket_comment
                WHERE tenant_id = ? AND ticket_id = ? AND (? OR internal = false)
                ORDER BY created_at, id
                """,
                (result, row) ->
                        new TicketComment(
                                result.getObject("id", UUID.class),
                                result.getObject("tenant_id", UUID.class),
                                result.getObject("ticket_id", UUID.class),
                                result.getObject("author_id", UUID.class),
                                result.getString("body"),
                                result.getBoolean("internal"),
                                result.getTimestamp("created_at").toInstant()),
                tenantId,
                ticketId,
                includeInternal);
    }

    @Override
    public void appendAudit(
            UUID tenantId,
            UUID ticketId,
            UUID actorId,
            TicketAuditAction action,
            String oldValue,
            String newValue,
            Instant occurredAt) {
        jdbc.update(
                """
                INSERT INTO ticket.ticket_audit
                    (id, tenant_id, ticket_id, actor_id, action, old_value, new_value, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                ticketId,
                actorId,
                action.name(),
                oldValue,
                newValue,
                Timestamp.from(occurredAt));
    }
}
