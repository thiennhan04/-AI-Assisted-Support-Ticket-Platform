# Local Development and Repository Bootstrap

## 1. Recommended repository layout

Use a monorepo for portfolio development while preserving service boundaries:

```text
ticket-ai-platform/
  services/
    identity-service/
    ticket-service/
    ai-orchestrator-service/
    knowledge-service/
    ai-worker/
  libs/
    event-contracts/
    test-support/
  deploy/
    compose.yaml
    prometheus/
    grafana/
    otel/
  docs/
  scripts/
  .github/workflows/
```

Do not create a shared domain-model library. Only event DTOs and test utilities may be shared. This prevents accidental database/entity coupling.

## 2. Per-service bootstrap

Spring Initializr dependencies:

- Spring Web.
- Validation.
- Spring Security and OAuth2 Resource Server.
- Spring Data JPA.
- Flyway.
- PostgreSQL driver.
- Actuator.
- Micrometer tracing/OTLP.
- AMQP where publishing/consuming.
- Testcontainers PostgreSQL/RabbitMQ.

AI Orchestrator adds Spring AI model integration. Knowledge Service adds pgvector support and document extraction libraries. Pin dependency versions through a root BOM or Renovate-controlled properties.

## 3. Local containers

Compose should provide:

| Container | Local port |
|---|---:|
| PostgreSQL | 5432 |
| RabbitMQ AMQP/UI | 5672/15672 |
| Redis | 6379 |
| MinIO API/UI | 9000/9001 |
| OpenTelemetry Collector | 4317/4318 |
| Prometheus | 9090 |
| Grafana | 3000 |

Use one PostgreSQL container with separate local databases/users: `identity_db`, `ticket_db`, `ai_db`, `knowledge_db`.

## 4. Developer run sequence

1. Start infrastructure: `docker compose up -d postgres rabbitmq redis minio otel prometheus grafana`.
2. Start Identity, Ticket, Knowledge and AI Orchestrator with `local` profile.
3. Start Worker last.
4. Run local bootstrap script to create tenant/admin/sample knowledge.
5. Import `contracts/openapi.yaml` into an API client or generate frontend client.
6. Use fake AI/embedding providers until infrastructure flow passes.
7. Set provider secret locally outside repository to test real model.

### Run Identity Service locally (DD-101)

From the repository root on Windows PowerShell:

```powershell
docker compose -f deploy/compose.yaml up -d postgres redis
.\scripts\generate-local-rsa-keys.ps1
$env:SPRING_PROFILES_ACTIVE = "local"
$env:LOCAL_SEED_ENABLED = "true"
$env:LOCAL_SEED_PASSWORD = "ChangeMe123!"
.\mvnw.cmd -pl services/identity-service -am spring-boot:run
```

The opt-in seed creates tenant `acme` and three users: `admin@acme.local`,
`agent@acme.local`, and `customer@acme.local`. They use the password supplied through
`LOCAL_SEED_PASSWORD`. Disable the seed outside local development and never commit private keys;
`.local/` is ignored by Git.

Login example:

```powershell
$body = @{
  tenantCode = "acme"
  email = "admin@acme.local"
  password = $env:LOCAL_SEED_PASSWORD
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/auth/login `
  -ContentType application/json -Body $body
```

Public key discovery is available at `http://localhost:8081/.well-known/jwks.json`. The
private RSA key is used only by Identity to sign access tokens and is never returned.

## 5. Local fake provider behavior

The fake provider must be deterministic:

- Subject containing `password` -> category ACCOUNT.
- Subject containing `payment` -> category PAYMENT.
- Draft reply cites the first returned chunk.
- Special marker `[INVALID_JSON]` triggers invalid-output test.
- Special marker `[TIMEOUT]` triggers timeout test.

This makes end-to-end tests reliable without paid API/network.

## 6. Health endpoints

- `/actuator/health/liveness`: process/JVM only.
- `/actuator/health/readiness`: required DB/broker for service responsibility.
- `/actuator/prometheus`: private network only.
- `/actuator/info`: build commit/version, no secrets.

## 7. CI pipeline

For each pull request:

1. Compile with warnings visible.
2. Unit and architecture tests.
3. Integration tests with Testcontainers.
4. OpenAPI lint and backward-compatibility check.
5. Event schema validation.
6. Static analysis and dependency vulnerability scan.
7. Build immutable container image tagged with commit SHA.

Main branch additionally runs end-to-end fake-provider suite and pushes images.
