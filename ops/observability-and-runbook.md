# Observability and Operations Runbook

## 1. Service-level indicators

### Core API

- Availability: successful eligible requests / total eligible requests.
- Latency: p50/p95/p99 by route template, not raw URI.
- Correctness: 5xx rate and domain-conflict rate separately.

### AI pipeline

- Job success rate by job type/model/prompt version.
- End-to-end queue-to-terminal latency.
- Schema-validation/repair rate.
- Citation rejection/no-knowledge rate.
- Token/cost per successful job.

### Knowledge

- Ingestion success/duration/pages/chunks.
- Search p95 and no-result rate.
- Offline Recall@5 evaluation by corpus version.

## 2. Suggested alerts

| Alert | Condition | Severity |
|---|---|---|
| Core API error | 5xx > 2% for 5 min | high |
| Core latency | p95 > target for 10 min | medium |
| DLQ non-empty | count > 0 for 5 min | high |
| AI provider circuit open | > 2 min | medium |
| AI queue lag | oldest > 5 min | medium |
| DB pool saturation | active/max > 90% for 5 min | high |
| Knowledge ingest failures | > 5 in 15 min | medium |
| Tenant budget near limit | > 80% | informational |

## 3. Dashboards

Create three Grafana dashboards:

1. Platform overview: request rate/errors/latency, instance health, DB/broker.
2. AI quality/cost: jobs, failures, token/cost, prompt versions, repair and feedback.
3. Knowledge: ingestion queue/status, chunk count, retrieval latency/no-results.

## 4. Runbook: AI jobs delayed

1. Check Worker instances and queue oldest-message age.
2. Check provider circuit and 429/5xx rate.
3. Verify tenant budget and concurrency settings.
4. Scale Worker only if provider and DB have capacity.
5. If messages are in DLQ, inspect safe error code/correlation ID.
6. Fix root cause and replay preserving event/job IDs.
7. Verify duplicate protection and job terminal state.

## 5. Runbook: incorrect/leaking answer

1. Disable `FEATURE_AI_DRAFT` if active risk exists.
2. Capture job ID, prompt version, model, citation IDs and access decision logs.
3. Confirm Knowledge ACL filter for affected principal/tenant.
4. Reproduce in secured staging with sanitized fixture.
5. If prompt issue, create a new immutable prompt version and evaluation case.
6. If retrieval issue, fix index/ACL and re-run evaluation.
7. Do not edit historical job/output records.

## 6. Runbook: stuck document ingestion

1. Inspect document version status and lease expiry.
2. Confirm object exists and checksum/size match.
3. Check extractor and embedding batch errors.
4. Clean staging chunks for failed version only.
5. Retry same version/job ID when safe.
6. Previous active version must remain searchable throughout.

