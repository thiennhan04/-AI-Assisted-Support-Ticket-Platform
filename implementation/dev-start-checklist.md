# Developer Start Checklist

Use this list before coding a story.

## Platform setup

- [ ] Java 21 and container runtime installed.
- [ ] Local infrastructure healthy.
- [ ] Service database/user created and Flyway baseline applied.
- [ ] Fake AI/embedding provider selected.
- [ ] OpenAPI imported and event samples available.

## For every endpoint

- [ ] Role and tenant policy defined.
- [ ] Request limits/validation defined.
- [ ] Idempotency and optimistic concurrency decision made.
- [ ] Transaction boundary identified.
- [ ] Audit/outbox requirement identified.
- [ ] Problem code documented.
- [ ] Unit, security and integration cases written.

## For every event

- [ ] Producer transaction/outbox identified.
- [ ] Routing key and payload version registered.
- [ ] PII minimized.
- [ ] Consumer idempotency key/transaction defined.
- [ ] Retryable/permanent errors classified.
- [ ] DLQ and replay behavior documented.
- [ ] Current and duplicate-delivery tests exist.

## For every AI feature

- [ ] Business fallback works without AI.
- [ ] Input/output schema and limits defined.
- [ ] Prompt version is immutable/audited.
- [ ] Data classification/redaction checked.
- [ ] Grounded claims/citations validated where needed.
- [ ] Human approval point explicit.
- [ ] Token, cost, latency and quality measured.
- [ ] Evaluation case added before prompt change is accepted.

## Pull request completion

- [ ] Build, unit, integration, architecture and contract tests pass.
- [ ] Migration tested from clean and previous schema.
- [ ] Metrics/logs contain no raw user content or secrets.
- [ ] API/event docs updated.
- [ ] Rollback/degradation behavior stated.
- [ ] Acceptance criteria demonstrated with test or screenshot/log evidence.

