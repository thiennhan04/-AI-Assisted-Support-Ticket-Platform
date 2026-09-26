# DD-201 Contract Decisions

## Tenant and caller identity

- `tenantId` and `requesterId` never come from request input. They are derived from the verified
  `AuthenticatedPrincipal` created by DD-103.
- Every repository lookup includes `tenant_id`. A ticket in another tenant is returned as
  `404 TICKET_NOT_FOUND` to avoid revealing its existence.
- CUSTOMER list and detail access are additionally restricted to tickets where the caller is the
  requester. AGENT and ADMIN can view all tickets in their tenant.
- Unknown JSON properties are rejected, so sending a client-controlled `tenantId` fails with 400.

## Commands and authorization

- Any authenticated platform role can create a ticket; the caller becomes its requester.
- CUSTOMER can edit subject/description only on their own OPEN ticket.
- AGENT and ADMIN can change content, priority, category, and assignment within their tenant.
- CUSTOMER may perform only the requester-side transitions from `WAITING_CUSTOMER` to
  `IN_PROGRESS` and from `RESOLVED` to `IN_PROGRESS` or `CLOSED`.
- AGENT/ADMIN perform support-side transitions. Reopening `CLOSED` is ADMIN-only.
- The assignee UUID is currently an opaque authorized reference. Checking that it represents an
  active same-tenant agent is deferred until an Identity user-directory contract is available.

## State and persistence

- Ticket numbers use the database sequence `ticket.ticket_number_seq` and the format
  `SUP-%08d`; UUID remains the canonical identifier.
- `contentVersion` increments only when subject or description changes. JPA `version` protects the
  persisted aggregate and is returned to clients.
- The Flyway migration owns the Ticket schema, constraints, indexes, and full-text GIN index.
- DD-201 exposes `q` behavior as case-insensitive subject/description matching. Query-plan tuning to
  use PostgreSQL full-text operators may be introduced without changing the HTTP contract.

## Deliberate epic boundaries

- The published OpenAPI already describes `Idempotency-Key` and `If-Match`/ETag as the target API.
  Enforcement is DD-202, so DD-201 does not pretend those guarantees exist yet.
- Comments/audit are DD-203. Transactional outbox publishing is DD-204.
