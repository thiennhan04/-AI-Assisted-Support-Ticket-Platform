# DD-002 Local Infrastructure

This directory contains the local infrastructure required by the five application modules.
It does not run the application images themselves.

## What is provisioned

- PostgreSQL 16 with pgvector and one persistent volume.
- Four service-owned databases and users: `identity_db`, `ticket_db`, `ai_db`, and
  `knowledge_db`.
- The `vector` extension in `knowledge_db`.
- RabbitMQ with the management UI, Redis with AOF persistence, and MinIO with a private
  `knowledge` bucket.
- OpenTelemetry Collector, Prometheus, and a provisioned Grafana data source/dashboard.

All images are pinned. All stateful components use named Docker volumes. Local credentials
come from the root `.env` file and have development-only defaults in `.env.example`.

## Start

From the repository root:

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f deploy/compose.yaml up -d
docker compose --env-file .env -f deploy/compose.yaml ps
```

The one-shot `minio-init` container should finish with exit code 0. Long-running containers
should report `healthy`.

## Local endpoints

| Component | Endpoint |
|---|---|
| PostgreSQL | `localhost:5432` |
| RabbitMQ | `amqp://localhost:5672` |
| RabbitMQ UI | `http://localhost:15672` |
| Redis | `localhost:6379` |
| MinIO API | `http://localhost:9000` |
| MinIO Console | `http://localhost:9001` |
| OTLP gRPC/HTTP | `localhost:4317` / `localhost:4318` |
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3000` |

Use credentials from `.env`. Do not use the example credentials outside a local machine.

## Stop or reset

Stop containers while preserving data:

```powershell
docker compose --env-file .env -f deploy/compose.yaml down
```

Starting again reuses the named volumes. To intentionally delete all DD-002 local data, add
`--volumes` to the command. That operation is destructive and is not part of normal cleanup.
