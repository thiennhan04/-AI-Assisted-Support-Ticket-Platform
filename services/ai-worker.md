# AI Worker - Detail Design

## 1. Responsibilities

- Consume ticket and knowledge events.
- Translate events into internal service commands.
- Execute retry policy without duplicating business state.
- Publish operational failure/reconciliation signals.

The Worker does not store domain state and does not call the LLM directly.

## 2. Consumers

| Queue | Routing keys | Handler |
|---|---|---|
| `ai.ticket-analysis.q` | `ticket.created.v1`, `ticket.analysis.requested.v1` | `TicketAnalysisConsumer` |
| `ai.ticket-draft.q` | `ticket.draft.requested.v1` | `TicketDraftConsumer` |
| `knowledge.ingest.q` | `knowledge.ingest.requested.v1` | `KnowledgeIngestConsumer` |

Each queue has a retry exchange and DLQ.

## 3. Processing template

1. Deserialize envelope and validate schema version.
2. Put trace/correlation/tenant metadata in context.
3. Acquire Redis lease `worker:{consumer}:{eventId}` with 90-second TTL.
4. Call owning internal endpoint using `eventId`/`jobId` as idempotency identity.
5. Acknowledge only on terminal success or confirmed idempotent duplicate.
6. On retryable exception, reject to retry route with incremented attempt header.
7. On permanent validation/authorization error, publish failure event and acknowledge.
8. Clear context and release lease.

Redis lease is an optimization, not the correctness boundary. Correctness comes from service-side unique constraints.

## 4. Retry schedule

Attempts after initial delivery: 10 seconds, 1 minute, 5 minutes, 30 minutes. Then DLQ. Preserve original `eventId`; add `attempt`, `firstOccurredAt`, `lastErrorCode` headers.

Retryable:

- HTTP 408, 429, 502, 503, 504.
- connection/timeout failures.
- transient database or broker failures.

Permanent:

- invalid event schema.
- subject not found after reconciliation delay.
- tenant mismatch.
- unsupported job type.
- provider policy rejection.

## 5. Concurrency

- Ticket analysis prefetch: 10 per instance.
- Draft reply prefetch: 5 per instance.
- Knowledge ingest prefetch: 2 per instance.
- Separate thread pools prevent large document ingestion from starving ticket analysis.
- Graceful shutdown stops intake and allows 60 seconds for in-flight work.

## 6. Reconciliation

A scheduled reconciliation job runs every 15 minutes:

- Finds Ticket Service AI requests still `QUEUED` beyond five minutes.
- Checks AI Orchestrator by job ID.
- Republishes missing requests using same job/event identity.
- Never creates a new logical job ID.

## 7. Tests

- Duplicate delivery produces one job.
- Retryable vs permanent classification.
- Retry headers and DLQ behavior.
- Trace propagation.
- Graceful shutdown.
- Concurrency pool isolation.

