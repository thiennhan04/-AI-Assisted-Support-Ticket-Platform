# Ticket Service - Detail Design

## 1. Responsibilities

- Ticket lifecycle, assignment, comments and attachment metadata.
- Authorization and tenant isolation.
- Audit log and optimistic concurrency.
- Transactional outbox.
- Read projection of AI analysis/drafts for UI.

## 2. Package structure

```text
com.portfolio.ticket
  api
    TicketController
    CommentController
    AiFeatureController
  application
    TicketCommandService
    TicketQueryService
    AiProjectionService
    AttachmentService
  domain
    Ticket
    TicketStatus
    TicketPolicy
    TicketRepository
  infrastructure
    persistence
    messaging
    storage
    security
```

## 3. Domain commands

| Command | Allowed roles | Main validation |
|---|---|---|
| CreateTicket | authenticated | requester is caller unless Agent/Admin |
| UpdateContent | requester while OPEN, Agent/Admin | expected version required |
| AssignTicket | Agent/Admin | assignee is active agent in tenant |
| ChangeStatus | depends on transition | state-machine rule |
| AddComment | ticket participant/Admin | non-empty, <= 10,000 chars |
| RequestAiDraft | Agent/Admin | ticket not CLOSED, sufficient content |
| ApproveAiDraft | Agent/Admin | draft exists and unchanged |

## 4. API behavior

### Create

`POST /v1/tickets` requires `Idempotency-Key` UUID. Store request hash and response reference for 24 hours. Reusing a key with different body returns `409 IDEMPOTENCY_KEY_REUSED`.

Ticket number format: tenant-configurable prefix plus zero-padded sequence, e.g. `SUP-00001234`. Internal UUID remains canonical identifier.

### Query

`GET /v1/tickets` supports `status`, `priority`, `category`, `assigneeId`, `requesterId`, `createdFrom`, `createdTo`, `q`, `page`, `size`, `sort`. Maximum size 50. CUSTOMER queries are always restricted to caller's tickets.

### Update

Every mutable command accepts `If-Match: "<version>"`. Missing header returns `428 PRECONDITION_REQUIRED`; stale version returns `412 TICKET_VERSION_CONFLICT` with current version.

### AI endpoints

- `POST /v1/tickets/{id}/ai-analysis/retry` -> `202`.
- `POST /v1/tickets/{id}/ai-drafts` -> `202 { jobId, status }`.
- `GET /v1/tickets/{id}/ai-drafts/{draftId}`.
- `POST /v1/tickets/{id}/ai-drafts/{draftId}/approve` creates a normal agent comment from editable request content.
- `POST /v1/tickets/{id}/ai-feedback` records rating/reason through an event.

## 5. Persistence and indexes

Primary tables: `ticket`, `ticket_comment`, `ticket_attachment`, `ticket_ai_analysis`, `ticket_ai_draft`, `ticket_audit`, `idempotency_record`, `outbox_event`, `processed_event`.

Required indexes:

- `(tenant_id, status, updated_at desc)`.
- `(tenant_id, assignee_id, status)`.
- `(tenant_id, requester_id, created_at desc)`.
- GIN full-text index on subject/description for PostgreSQL search.
- Unique `(tenant_id, ticket_number)`.

## 6. Event projection

On `ai.analysis.completed.v1`:

1. Insert `processed_event`; exit success on duplicate.
2. Confirm tenant and ticket exist.
3. Upsert analysis by `ai_job_id`.
4. Do not overwrite accepted ticket category/priority.
5. Append audit record `AI_ANALYSIS_RECEIVED`.

On draft completion, upsert draft and status. A completed draft is immutable; user edits are passed only when approving as a comment.

## 7. Attachment flow

Use signed direct upload:

1. `POST /v1/tickets/{id}/attachments/uploads` creates `PENDING` metadata and returns signed PUT URL.
2. Client uploads object.
3. `POST .../{attachmentId}/complete` verifies size, checksum and content type, then marks `ACTIVE`.
4. Malware scanning may hold status at `SCANNING`; unscanned objects are never downloadable.

## 8. Error codes

- `TICKET_NOT_FOUND` returns 404 even when cross-tenant to avoid enumeration.
- `TICKET_INVALID_TRANSITION` 409.
- `TICKET_VERSION_CONFLICT` 412.
- `TICKET_FORBIDDEN` 403 only when existence may safely be disclosed.
- `AI_DRAFT_NOT_READY` 409.

## 9. Tests

- All state transitions and invalid transitions.
- Role/tenant matrix.
- Optimistic locking under concurrent update.
- Idempotent create and mismatched replay.
- Outbox written atomically with ticket.
- Duplicate AI event ignored.
- AI failure does not change ticket status.

