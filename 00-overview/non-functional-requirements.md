# Non-Functional Requirements

## 1. Availability and degradation

- Core ticket read/write APIs: target 99.9% monthly availability.
- Identity token endpoints: target 99.9% monthly availability.
- AI features: target 99.0%; failure must not reduce core API availability.
- Readiness becomes unhealthy only when the service cannot perform core responsibility. Model-provider failure does not mark Ticket Service unhealthy.

## 2. Performance targets

| Operation | Target p95 | Notes |
|---|---:|---|
| Login | 500 ms | excludes external identity providers |
| Create/update ticket | 600 ms | AI excluded |
| Ticket list | 800 ms | page size <= 50 |
| Knowledge vector search | 800 ms | topK <= 20 |
| AI analysis | 15 s | asynchronous |
| Grounded draft | 25 s | asynchronous |

## 3. Scale baseline

- 100 tenants.
- 10,000 active users.
- 1,000,000 tickets total.
- 100 ticket writes/second burst.
- 20 AI jobs/second sustained.
- 1,000,000 knowledge chunks.
- Maximum ticket body: 20,000 Unicode characters.
- Maximum document: 25 MB and 500 pages.

## 4. Security

- TLS 1.2+ for all network paths.
- Secrets from secret manager/environment injection only.
- Password hashing with Argon2id or BCrypt cost 12 minimum.
- Tenant scope enforced server-side on every query.
- Object keys are opaque and never derived directly from user filenames.
- AI input is classified and redacted according to policy before provider call.
- Audit records are append-only at application level.

## 5. Data retention

| Data | Default retention |
|---|---|
| Ticket/audit data | 3 years or tenant policy |
| Refresh tokens | expiry + 30 days metadata |
| AI raw input/output | 30 days |
| AI usage/quality metrics | 13 months |
| Deleted document objects | purge within 7 days |
| Application logs | 30 days |

## 6. Observability

Every request and event must carry:

- `trace_id` and `span_id`.
- `correlation_id` exposed to client.
- `tenant_id` as controlled metadata, never raw PII.
- Service/version/environment attributes.

Mandatory metrics:

- HTTP request count, duration and error rate.
- DB pool utilization and query duration.
- queue depth, consumer lag, redelivery and DLQ count.
- AI jobs by type/status.
- model latency, tokens, estimated cost and schema-validation failure.
- retrieval latency, result count and no-result rate.

## 7. Maintainability

- Java 21 and one supported Spring Boot minor line.
- Public APIs and events use explicit versioning.
- Database changes use forward-only Flyway migrations.
- Each service has >= 70% line coverage as a guardrail; critical domain classes require behavior-focused tests.
- Architecture tests enforce package dependency rules.

