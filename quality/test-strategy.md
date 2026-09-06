# Test and Evaluation Strategy

## 1. Test pyramid

- Unit tests: domain policies, state machines, prompt/output validators, chunking.
- Slice tests: controllers/security/repositories.
- Integration tests: real PostgreSQL/RabbitMQ/Redis/MinIO using Testcontainers.
- Contract tests: OpenAPI and event schemas.
- End-to-end: full platform with fake AI and fake embeddings.
- AI evaluation: fixed datasets against real provider, scheduled/manual due cost.

## 2. Per-service minimum suites

### Identity

- Token claims, expiry and rotation.
- Password verification and rate limiting.
- Refresh concurrency/reuse detection.
- Admin tenant isolation.

### Ticket

- Every role x action x ownership combination.
- State-machine transitions.
- optimistic locking and idempotency.
- outbox atomicity and duplicate consumer event.

### AI Orchestrator

- Job idempotency and input hash conflict.
- Strict schema validation and single repair.
- citation subset validation.
- provider error classification, retry and circuit breaker.
- prompt/model version audit.

### Knowledge

- extraction/chunking fixtures.
- version activation atomicity.
- ACL filtering before ranking.
- vector/hybrid ranking fixtures.
- deletion removes search visibility immediately.

### Worker

- retry routing, DLQ and preserved IDs.
- concurrency pool isolation.
- reconciliation without new logical job.

## 3. Contract tests

- Lint `openapi.yaml` and generate a client during CI to prove usability.
- Compare external API against main branch; breaking changes fail unless versioned.
- Validate event samples against JSON schemas.
- Consumer tests use producer fixtures for current and previous supported versions.

## 4. AI evaluation datasets

### Ticket classification dataset

CSV/JSONL fields: `caseId`, subject, description, expected category, expected priority range, sensitive-data flag. Minimum 100 hand-reviewed cases split 70 development/30 locked test.

Metrics:

- Macro F1 for category.
- Exact/adjacent accuracy for priority.
- Structured-output validity.
- Sensitive-data redaction recall.
- p50/p95 latency and mean cost.

### RAG dataset

Fields: query, allowed document IDs, expected supporting chunk/document, unanswerable flag, principal/role.

Metrics:

- Recall@5 and MRR for retrieval.
- citation validity (must be 100% structurally).
- answer groundedness scored by rubric and sampled human review.
- correct refusal/no-knowledge rate.
- ACL leakage count (must be zero).

## 5. Release gates

- No critical/high security finding.
- All migrations apply from clean DB and previous release snapshot.
- Contract compatibility passes.
- Cross-tenant test matrix passes 100%.
- Citation structural validity 100%.
- No ACL leakage in evaluation.
- Category Macro F1 target >= 0.80 for portfolio baseline.
- Retrieval Recall@5 target >= 0.85 on controlled corpus.
- Core ticket API p95 within NFR target under baseline load.

Targets are initial hypotheses. Record actual dataset and method in README; never claim unmeasured numbers.

## 6. Performance scenarios

1. 100 concurrent ticket list users, 20 writes/s for 15 minutes.
2. 20 AI jobs/s with fake provider latency distribution.
3. Queue backlog of 10,000 events and recovery without loss/duplication.
4. Search over one million synthetic chunks.
5. 100 simultaneous refresh requests for the same token; exactly one succeeds.

## 7. Test data rules

- Use synthetic data only; never company/customer documents.
- Seed deterministic tenants/users and stable UUIDs for integration tests.
- Separate locked evaluation test cases from prompt iteration data.
- Version datasets alongside prompt/config changes.

