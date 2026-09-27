# DD-202 Implementation Note

## What changed

- `POST /v1/tickets` now requires a UUID `Idempotency-Key`.
- The first request stores a SHA-256 hash of the create command and its ticket ID for 24 hours.
- Replaying the same key and content returns the original ticket; different content returns
  `409 IDEMPOTENCY_KEY_REUSED`.
- Create, detail, and update responses include an ETag such as `"0"`.
- `PATCH /v1/tickets/{id}` requires that value in `If-Match`. A missing header returns 428; a stale
  version returns `412 TICKET_VERSION_CONFLICT` with `currentVersion`.
- Flyway `V002` creates `ticket.idempotency_record`. A PostgreSQL transaction advisory lock makes
  simultaneous retries for the same tenant, requester, and key run one at a time.

## Short review path

1. Read the three DD-202 tests in `TicketCrudIT`: replay, key reuse, and stale `If-Match`.
2. Read `TicketController` to see the two HTTP headers and ETag responses.
3. Read `TicketCommandService.create` and `update` for the complete business flow.
4. Read `TicketCreationIdempotencyRepository` for the small persistence contract.
5. Read `JdbcTicketCreationIdempotencyRepository` and Flyway `V002` for PostgreSQL details.

No new domain abstraction was added because idempotency and HTTP preconditions protect commands;
they do not change the Ticket lifecycle rules.

## Verification

`./mvnw.cmd -pl services/ticket-service -am clean verify` passed with 10 unit/security tests and 11
PostgreSQL Testcontainers integration tests. Formatter, compiler, Enforcer, Flyway V001/V002, JAR
packaging, unit tests, and integration tests all completed successfully.
