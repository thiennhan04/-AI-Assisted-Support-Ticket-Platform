package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.domain.Category;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket", schema = "ticket")
class TicketJpaEntity {

    @Id private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "ticket_number", nullable = false, length = 30)
    private String number;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(nullable = false, length = 300)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(name = "content_version", nullable = false)
    private long contentVersion;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected TicketJpaEntity() {}

    private TicketJpaEntity(Ticket ticket) {
        id = ticket.id();
        tenantId = ticket.tenantId();
        number = ticket.number();
        requesterId = ticket.requesterId();
        assigneeId = ticket.assigneeId();
        subject = ticket.subject();
        description = ticket.description();
        category = ticket.category();
        priority = ticket.priority();
        status = ticket.status();
        contentVersion = ticket.contentVersion();
        version = ticket.version();
        createdAt = ticket.createdAt();
        updatedAt = ticket.updatedAt();
        closedAt = ticket.closedAt();
    }

    static TicketJpaEntity fromDomain(Ticket ticket) {
        return new TicketJpaEntity(ticket);
    }

    Ticket toDomain() {
        return Ticket.rehydrate(
                id,
                tenantId,
                number,
                requesterId,
                assigneeId,
                subject,
                description,
                category,
                priority,
                status,
                contentVersion,
                version,
                createdAt,
                updatedAt,
                closedAt);
    }
}
