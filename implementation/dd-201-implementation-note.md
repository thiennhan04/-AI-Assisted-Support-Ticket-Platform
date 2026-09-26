# DD-201 Implementation Note

## Delivered scope

- Added a Ticket aggregate with validation, content versioning, assignment behavior, and the full
  lifecycle state machine.
- Added principal-based authorization for CUSTOMER, AGENT, and ADMIN and fail-closed tenant
  isolation.
- Added `POST /v1/tickets`, `GET /v1/tickets/{ticketId}`, `GET /v1/tickets`, and
  `PATCH /v1/tickets/{ticketId}`.
- Added filtering by status, priority, category, assignee, requester, creation time, and text, plus
  bounded pagination and allow-listed sorting.
- Added JPA persistence and Flyway `V001` with constraints, sequence, tenant indexes, and a
  PostgreSQL full-text GIN index.
- Added Problem Details responses with stable ticket error codes and correlation IDs.
- Refactored the aggregate factory, authorization policy, command orchestration, and integration
  test vocabulary so lifecycle intent is visible without decoding raw JSON or anonymous UUIDs.
- Added `dd-201-reading-guide.md` as a production-first review path for new developers.

## Verification

- Domain tests cover all allowed transitions, rejected transitions, closed-ticket immutability,
  content-version increments, and assignment behavior.
- Eight PostgreSQL Testcontainers integration tests separately cover authentication,
  request-derived-tenant rejection, creation defaults, role/tenant visibility, management policy,
  lifecycle transitions, invalid transitions, filtering, and pagination.
- `mvn verify` does not require a developer-owned PostgreSQL instance; the integration database is
  disposable and managed by Testcontainers.
- Full monorepo `./mvnw.cmd -B clean verify` passed all modules and 48 tests. Ticket Service
  contributed 10 unit/security tests and 8 PostgreSQL integration tests.
- The initial DD-201 image was `ticket-platform/ticket-service:0.1.0-SNAPSHOT` (`46887b60424f`).
  Rebuild it after committing the readability refactor so the image and source revision stay
  traceable together.

## Deferred by design

- DD-202: `Idempotency-Key`, ETag/`If-Match`, and the externally visible optimistic-concurrency
  response contract.
- DD-203: comments and audit trail.
- DD-204: transactional outbox and ticket events.
