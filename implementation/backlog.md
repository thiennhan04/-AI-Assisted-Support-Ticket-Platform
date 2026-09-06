# Implementation Backlog and Acceptance Criteria

## Epic 0 - Repository and infrastructure

### DD-001 Bootstrap monorepo

- Create five Spring Boot modules and allowed shared libraries.
- Add Java 21 toolchain, formatting, test and container build.
- Acceptance: all modules compile/test from root; no service domain dependency.

### DD-002 Local infrastructure

- Compose PostgreSQL databases/users, RabbitMQ, Redis, MinIO, OTel, Prometheus and Grafana.
- Acceptance: health checks green; data volumes persist; credentials overridden through environment.

## Epic 1 - Identity

### DD-101 Login and JWT

- Implement tenant/user/roles, password hashing, login and RS256 signing.
- Acceptance: valid token contains required claims; wrong credentials use generic 401.

### DD-102 Refresh rotation

- Implement rotating token family, logout and reuse detection.
- Acceptance: two concurrent refresh calls yield one success; reuse revokes family.

### DD-103 Resource-server integration

- Configure other services with JWKS validation and principal mapping.
- Acceptance: wrong issuer/audience/tenant claim rejected; key rotation fixture passes.

## Epic 2 - Ticket core

### DD-201 Ticket CRUD/query

- Implement aggregate, state machine, list filters and authorization.
- Acceptance: role/tenant matrix and all transitions tested.

### DD-202 Optimistic locking/idempotency

- Add ETag/If-Match and Idempotency-Key handling.
- Acceptance: stale update returns 412; identical create replay returns original resource.

### DD-203 Comments and audit

- Add public/internal comments and append-only audit.
- Acceptance: customer cannot see internal comments; every mutation emits audit.

### DD-204 Transactional outbox

- Persist/publish ticket events using broker confirms.
- Acceptance: simulated publish failure leaves retryable row; no lost event after restart.

## Epic 3 - Knowledge

### DD-301 Upload workflow

- Implement document metadata, signed upload and completion verification.
- Acceptance: MIME/size/checksum enforced; incomplete uploads expire.

### DD-302 Extraction and chunking

- Implement PDF/DOCX/TXT adapters and deterministic chunker.
- Acceptance: fixtures preserve page/heading metadata; empty/encrypted documents fail safely.

### DD-303 Embedding and vector search

- Implement fake and real embedding ports plus pgvector query.
- Acceptance: active-version/tenant/ACL filter occurs in SQL; Recall@5 evaluation runnable.

### DD-304 Version replacement/deletion

- Stage replacement and atomically switch active version.
- Acceptance: no search downtime; deletion removes visibility immediately.

## Epic 4 - AI Orchestration

### DD-401 AI job lifecycle

- Implement job idempotency, input hashing and state machine.
- Acceptance: repeated identical request reuses job; conflicting input returns 409.

### DD-402 Ticket analysis

- Implement prompt version, structured output and validation.
- Acceptance: output schema always validated; one repair only; metrics recorded.

### DD-403 Grounded draft

- Integrate Knowledge search and citation validation.
- Acceptance: fabricated citation rejected; no knowledge produces flagged/refusal-style draft.

### DD-404 Provider resilience/cost

- Implement timeouts, retry, circuit breaker, token/cost record and budget.
- Acceptance: provider outage does not affect ticket API; exhausted tenant makes no provider call.

## Epic 5 - Worker and projections

### DD-501 Event consumers/retry/DLQ

- Implement separate queues/pools and retry schedule.
- Acceptance: duplicate delivery creates one job; poison message reaches DLQ with safe error.

### DD-502 Ticket AI projections

- Consume analysis/draft results idempotently.
- Acceptance: out-of-date result marked stale; accepted ticket fields not overwritten.

### DD-503 Draft approval/feedback

- Add agent approval as normal comment and feedback event.
- Acceptance: original draft immutable; edited sent content audited.

## Epic 6 - Quality and operations

### DD-601 Observability

- Add metrics/tracing/dashboards and alerts.
- Acceptance: one trace spans ticket event, Worker, Orchestrator, Knowledge and provider adapter.

### DD-602 Security hardening

- Add rate limits, upload scanning hook, PII redaction and prompt-injection fixtures.
- Acceptance: security tests in security design pass.

### DD-603 Evaluation and performance

- Add classification/RAG datasets and load scenarios.
- Acceptance: report actual metrics; release gates satisfied or exceptions documented.

### DD-604 Demo and documentation

- Add architecture diagram, API examples, demo seed and 2-minute scenario.
- Acceptance: a new developer can run fake-provider end-to-end flow from clean machine using documented commands.

## Suggested eight-week slicing

| Week | Deliverable |
|---|---|
| 1 | DD-001..002, DD-101, basic resource-server config |
| 2 | DD-201..203 |
| 3 | DD-204, DD-301..302 |
| 4 | DD-303..304 with fake embeddings |
| 5 | DD-401..402 with fake model |
| 6 | DD-403..404 and Worker consumers |
| 7 | projections, approval, security and observability |
| 8 | evaluation, load test, demo and CV evidence |

