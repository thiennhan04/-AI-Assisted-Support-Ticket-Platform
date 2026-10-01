package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.domain.TicketAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_audit", schema = "ticket")
class TicketAuditJpaEntity {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TicketAuditAction action;

    @Column(name = "old_value", length = 100)
    private String oldValue;

    @Column(name = "new_value", length = 100)
    private String newValue;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TicketAuditJpaEntity() {}

    TicketAuditJpaEntity(
            UUID id,
            UUID tenantId,
            UUID ticketId,
            UUID actorId,
            TicketAuditAction action,
            String oldValue,
            String newValue,
            Instant occurredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.ticketId = ticketId;
        this.actorId = actorId;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.occurredAt = occurredAt;
    }
}
