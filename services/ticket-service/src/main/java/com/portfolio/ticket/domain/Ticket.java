package com.portfolio.ticket.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Ticket {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS =
            Map.of(
                    TicketStatus.OPEN,
                    EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED),
                    TicketStatus.IN_PROGRESS,
                    EnumSet.of(TicketStatus.WAITING_CUSTOMER, TicketStatus.RESOLVED),
                    TicketStatus.WAITING_CUSTOMER,
                    EnumSet.of(TicketStatus.IN_PROGRESS),
                    TicketStatus.RESOLVED,
                    EnumSet.of(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS),
                    TicketStatus.CLOSED,
                    EnumSet.of(TicketStatus.IN_PROGRESS));

    private final UUID id;
    private final UUID tenantId;
    private final String number;
    private final UUID requesterId;
    private UUID assigneeId;
    private String subject;
    private String description;
    private Category category;
    private Priority priority;
    private TicketStatus status;
    private long contentVersion;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;

    private Ticket(
            UUID id,
            UUID tenantId,
            String number,
            UUID requesterId,
            UUID assigneeId,
            String subject,
            String description,
            Category category,
            Priority priority,
            TicketStatus status,
            long contentVersion,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.number = requireText(number, "number");
        this.requesterId = Objects.requireNonNull(requesterId);
        this.assigneeId = assigneeId;
        this.subject = validateSubject(subject);
        this.description = validateDescription(description);
        this.category = category;
        this.priority = Objects.requireNonNull(priority);
        this.status = Objects.requireNonNull(status);
        this.contentVersion = contentVersion;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.closedAt = closedAt;
    }

    public static Ticket open(
            UUID id,
            UUID tenantId,
            String number,
            UUID requesterId,
            String subject,
            String description,
            Priority priority,
            Instant now) {
        return new Ticket(
                id,
                tenantId,
                number,
                requesterId,
                null,
                subject,
                description,
                null,
                priority,
                TicketStatus.OPEN,
                0,
                0,
                now,
                now,
                null);
    }

    public static Ticket rehydrate(
            UUID id,
            UUID tenantId,
            String number,
            UUID requesterId,
            UUID assigneeId,
            String subject,
            String description,
            Category category,
            Priority priority,
            TicketStatus status,
            long contentVersion,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt) {
        return new Ticket(
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

    public void updateContent(String newSubject, String newDescription, Instant now) {
        requireMutable();
        var validatedSubject = validateSubject(newSubject);
        var validatedDescription = validateDescription(newDescription);
        if (subject.equals(validatedSubject) && description.equals(validatedDescription)) {
            return;
        }
        subject = validatedSubject;
        description = validatedDescription;
        contentVersion++;
        updatedAt = now;
    }

    public void changePriority(Priority newPriority, Instant now) {
        requireMutable();
        priority = Objects.requireNonNull(newPriority);
        updatedAt = now;
    }

    public void changeCategory(Category newCategory, Instant now) {
        requireMutable();
        category = Objects.requireNonNull(newCategory);
        updatedAt = now;
    }

    public void assign(UUID newAssigneeId, Instant now) {
        requireMutable();
        assigneeId = Objects.requireNonNull(newAssigneeId);
        updatedAt = now;
        if (status == TicketStatus.OPEN) {
            transitionTo(TicketStatus.IN_PROGRESS, now);
        }
    }

    public void transitionTo(TicketStatus target, Instant now) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(now);
        if (status == target) {
            return;
        }
        if (!canTransitionTo(target)) {
            throw new InvalidTicketTransitionException(status, target);
        }
        status = target;
        updatedAt = now;
        closedAt = target == TicketStatus.CLOSED ? now : null;
    }

    public boolean canTransitionTo(TicketStatus target) {
        return status == target
                || ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(target);
    }

    private void requireMutable() {
        if (status == TicketStatus.CLOSED) {
            throw new ClosedTicketMutationException();
        }
    }

    private static String validateSubject(String value) {
        var normalized = requireText(value, "subject");
        if (normalized.length() < 3 || normalized.length() > 300) {
            throw new IllegalArgumentException("subject must contain 3 to 300 characters");
        }
        return normalized;
    }

    private static String validateDescription(String value) {
        var normalized = requireText(value, "description");
        if (normalized.length() < 10 || normalized.length() > 20_000) {
            throw new IllegalArgumentException("description must contain 10 to 20000 characters");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String number() {
        return number;
    }

    public UUID requesterId() {
        return requesterId;
    }

    public UUID assigneeId() {
        return assigneeId;
    }

    public String subject() {
        return subject;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    public Priority priority() {
        return priority;
    }

    public TicketStatus status() {
        return status;
    }

    public long contentVersion() {
        return contentVersion;
    }

    public long version() {
        return version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant closedAt() {
        return closedAt;
    }
}
