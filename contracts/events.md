# Event Contracts

## 1. Envelope

All events use JSON UTF-8 and this envelope:

```json
{
  "eventId": "0191...uuid",
  "eventType": "ticket.created.v1",
  "occurredAt": "2026-08-24T10:15:30Z",
  "producer": "ticket-service",
  "correlationId": "uuid-or-trace-derived",
  "causationId": "source-event-id-or-null",
  "tenantId": "uuid",
  "subjectId": "uuid",
  "schemaVersion": 1,
  "data": {}
}
```

Rules:

- `eventId` is globally unique and immutable across retries.
- Consumers ignore unknown additive fields.
- Breaking payload changes require a new event type suffix.
- Timestamps are UTC ISO-8601.
- PII is minimized; attachments and secrets are never embedded.
- Broker persistence, publisher confirms and durable queues are required.

## 2. Exchange and routing

Topic exchange: `platform.domain.x`.

| Event type/routing key | Producer | Main consumer |
|---|---|---|
| `ticket.created.v1` | Ticket | AI Worker |
| `ticket.analysis.requested.v1` | Ticket | AI Worker |
| `ticket.draft.requested.v1` | Ticket | AI Worker |
| `ai.analysis.completed.v1` | AI Orchestrator | Ticket |
| `ai.analysis.failed.v1` | AI Orchestrator | Ticket |
| `ai.draft.completed.v1` | AI Orchestrator | Ticket |
| `ai.draft.failed.v1` | AI Orchestrator | Ticket |
| `ai.feedback.submitted.v1` | Ticket | AI Orchestrator |
| `knowledge.ingest.requested.v1` | Knowledge | AI Worker |
| `knowledge.document.activated.v1` | Knowledge | audit/metrics |

## 3. Payload definitions

### `ticket.created.v1`

```json
{
  "ticketId": "uuid",
  "ticketNumber": "SUP-00001234",
  "requesterId": "uuid",
  "subject": "Cannot reset password",
  "description": "...",
  "createdAt": "2026-08-24T10:15:30Z",
  "contentVersion": 0
}
```

### `ticket.draft.requested.v1`

```json
{
  "jobId": "uuid",
  "ticketId": "uuid",
  "requestedBy": "uuid",
  "locale": "en",
  "tone": "PROFESSIONAL",
  "ticketSnapshot": {
    "subject": "...",
    "description": "...",
    "latestComments": [{"authorType": "CUSTOMER", "body": "..."}],
    "contentVersion": 4
  }
}
```

### `ai.analysis.completed.v1`

```json
{
  "jobId": "uuid",
  "ticketId": "uuid",
  "inputContentVersion": 0,
  "summary": "Customer cannot reset the account password.",
  "suggestedCategory": "ACCOUNT",
  "suggestedPriority": "MEDIUM",
  "rationale": "Login access is blocked but no security incident is indicated.",
  "promptVersion": "ticket-analysis/1.0.0",
  "model": "configured-model-alias",
  "generatedAt": "2026-08-24T10:15:35Z"
}
```

Ticket Service marks a result as stale when `inputContentVersion` is older than current content version. It may display the result but must not apply suggestions automatically.

### `ai.draft.completed.v1`

```json
{
  "jobId": "uuid",
  "ticketId": "uuid",
  "inputContentVersion": 4,
  "reply": "Please follow the password reset guide...",
  "citations": [
    {
      "documentId": "uuid",
      "chunkId": "uuid",
      "label": "Password Reset Guide, page 2",
      "excerpt": "Open Account Settings and select Reset Password..."
    }
  ],
  "riskFlags": [],
  "promptVersion": "draft-reply/1.0.0",
  "model": "configured-model-alias",
  "generatedAt": "2026-08-24T10:16:00Z"
}
```

### Failure events

Fields: `jobId`, `ticketId`, `errorCode`, `retryable`, `attemptCount`, `failedAt`. Never include raw provider error bodies because they may contain prompts or user content.

## 4. Delivery semantics

- At-least-once delivery.
- Producer writes outbox with domain transaction.
- Publisher marks row only after broker confirmation.
- Consumer applies business change and `processed_event` marker atomically.
- Message ordering is not assumed globally. State/version checks handle reorder.
- Poison messages move to DLQ after configured attempts.

## 5. Contract compatibility

- Event JSON schemas should be added under `contracts/event-schemas/` when coding begins.
- Producers run schema validation in unit tests.
- Consumers maintain fixtures for current and previous supported versions.
- Contract change PRs require producer and consumer owners.

