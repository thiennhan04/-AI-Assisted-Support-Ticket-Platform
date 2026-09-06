# Configuration Reference

## 1. Rules

- Environment variables configure deployment differences.
- Secrets use secret manager references/injection and are never committed.
- Fail fast at startup for missing required configuration.
- Use typed `@ConfigurationProperties` with validation instead of scattered `@Value`.
- Profile names: `local`, `test`, `staging`, `prod`.

## 2. Common variables

| Variable | Required | Example/non-secret meaning |
|---|:---:|---|
| `SPRING_PROFILES_ACTIVE` | yes | `local` |
| `SERVER_PORT` | yes | service port |
| `DB_URL` | yes | JDBC URL |
| `DB_USERNAME` | yes | service-owned DB account |
| `DB_PASSWORD` | secret | injected |
| `RABBITMQ_HOST` | yes | broker hostname |
| `RABBITMQ_USERNAME` | yes | service account |
| `RABBITMQ_PASSWORD` | secret | injected |
| `REDIS_URL` | worker/AI | Redis URI |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | yes | collector endpoint |
| `JWT_ISSUER` | yes | token issuer URL |
| `JWT_AUDIENCE` | yes | `ticket-platform` |
| `JWKS_URI` | non-identity | Identity JWKS URL |
| `INTERNAL_AUTH_*` | internal callers | workload credential config |

## 3. AI Orchestrator variables

| Variable | Default | Purpose |
|---|---:|---|
| `AI_PROVIDER` | `fake` local | adapter selection |
| `AI_MODEL_ANALYSIS` | none | provider model alias |
| `AI_MODEL_DRAFT` | none | provider model alias |
| `AI_API_KEY` | secret | provider credential |
| `AI_REQUEST_TIMEOUT` | `30s` | provider timeout |
| `AI_MAX_RETRIES` | `2` | retryable provider calls |
| `AI_MAX_INPUT_TOKENS` | `12000` | hard cap |
| `AI_MAX_OUTPUT_TOKENS` | `1500` | hard cap |
| `AI_DAILY_TENANT_BUDGET_USD` | `5.00` local | budget cap |
| `KNOWLEDGE_BASE_URL` | required | Knowledge Service internal URL |
| `AI_STORE_RAW_DAYS` | `30` | retention |

## 4. Knowledge variables

| Variable | Default | Purpose |
|---|---:|---|
| `OBJECT_STORE_ENDPOINT` | local MinIO | object API |
| `OBJECT_STORE_BUCKET` | `knowledge` | bucket |
| `OBJECT_STORE_ACCESS_KEY` | secret | credential |
| `OBJECT_STORE_SECRET_KEY` | secret | credential |
| `EMBEDDING_PROVIDER` | `fake` local | adapter |
| `EMBEDDING_MODEL` | configured | model/version |
| `EMBEDDING_DIMENSION` | `1536` | must match schema |
| `CHUNK_TARGET_TOKENS` | `700` | target size |
| `CHUNK_OVERLAP_TOKENS` | `100` | overlap |
| `SEARCH_TOP_K_MAX` | `20` | API cap |

## 5. Worker variables

- `WORKER_ANALYSIS_CONCURRENCY=10`
- `WORKER_DRAFT_CONCURRENCY=5`
- `WORKER_INGEST_CONCURRENCY=2`
- `WORKER_JOB_TIMEOUT=60s`
- `AI_ORCHESTRATOR_BASE_URL=http://ai-orchestrator-service:8083`
- `KNOWLEDGE_BASE_URL=http://knowledge-service:8084`

## 6. Feature flags

- `FEATURE_AI_ANALYSIS=true`
- `FEATURE_AI_DRAFT=true`
- `FEATURE_HYBRID_SEARCH=false` initially
- `FEATURE_PII_REDACTION=true`

Flags must be observable and evaluated server-side. Disabling AI leaves ticket CRUD unchanged and prevents new AI events.

