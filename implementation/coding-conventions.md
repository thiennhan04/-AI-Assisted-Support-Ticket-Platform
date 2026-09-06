# Coding Conventions and Implementation Skeleton

## 1. Layering

- Controllers map transport DTOs to application commands and do not contain business logic.
- Application services define transaction boundaries and orchestrate domain/ports.
- Domain objects enforce invariants and contain no Spring/JPA dependency where practical.
- Infrastructure implements persistence, broker, storage and model-provider ports.
- JPA entities may be separate from domain models for strict boundaries; for portfolio scope, a well-controlled aggregate entity is acceptable.

## 2. Naming

- Commands: verbs, e.g. `CreateTicketCommand`.
- Results/queries: `TicketDetail`, `TicketPageQuery`.
- Ports: capability names, e.g. `LanguageModelPort`, `KnowledgeSearchPort`.
- Adapters: technology names, e.g. `SpringAiLanguageModelAdapter`.
- Events: past tense and versioned, e.g. `TicketCreatedV1`.
- Error codes: stable uppercase snake case.

## 3. Transaction template

```java
@Transactional
public TicketId handle(CreateTicketCommand command, Principal principal) {
    policy.checkCreate(principal, command);
    var ticket = Ticket.create(ids.next(), numberGenerator.next(principal.tenantId()), command);
    repository.save(ticket);
    outbox.append(TicketCreatedV1.from(ticket, correlation.currentId()));
    return ticket.id();
}
```

Do not publish to RabbitMQ directly inside this transaction.

## 4. Tenant-safe repository

```java
public interface TicketRepository {
    Optional<Ticket> findById(TenantId tenantId, TicketId ticketId);
    Page<TicketSummary> search(TenantId tenantId, TicketFilter filter, PageRequest page);
    void save(Ticket ticket);
}
```

Code review should reject unscoped tenant entity lookups.

## 5. Problem response

Map domain exceptions to `application/problem+json` with `type`, `title`, HTTP `status`, stable `code`, safe `detail`, `instance`, `correlationId` and optional field violations. Do not expose stack trace, SQL or provider response.

## 6. REST client rules

- Configure connect/read timeout explicitly.
- Propagate W3C trace context and correlation ID.
- Authenticate with service identity.
- Retry only documented idempotent/retryable operations.
- Convert remote failures into typed exceptions at adapter boundary.
- Use circuit breaker for model/knowledge dependencies.

## 7. Event handler template

```java
@Transactional
public void handle(AiAnalysisCompletedV1 event) {
    if (!processedEventRepository.tryInsert(CONSUMER, event.eventId())) return;
    var ticket = repository.findById(event.tenantId(), event.ticketId())
        .orElseThrow(() -> new PermanentEventException("TICKET_NOT_FOUND"));
    ticket.project(event);
    audit.append(AuditEntry.aiAnalysisReceived(ticket, event));
}
```

## 8. DTO and validation

- Java records for immutable API/event DTOs.
- Bean Validation for transport shape; domain validates business rules again.
- Jackson configured for ISO dates, enum strings and safe limits.
- Structured AI output uses dedicated DTO/schema, never `Map<String,Object>` in application code.

## 9. Logging

Use parameterized structured logs. Include `event`, correlation ID, tenant ID, resource/job ID and stable error code. Avoid user content. One exception is logged once at the handling boundary.

## 10. Build quality

- Java formatter and import rules in CI.
- SpotBugs/Error Prone or equivalent.
- ArchUnit rules for layers and forbidden provider dependencies.
- Dependency vulnerability and secret scanning.
- No `latest` container tags or unpinned production dependencies.

