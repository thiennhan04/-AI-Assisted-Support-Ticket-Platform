# DD-002 Implementation Note

Implemented and verified on 2026-09-06.

## Added

- `deploy/compose.yaml` with pinned PostgreSQL/pgvector, RabbitMQ, Redis, MinIO,
  OpenTelemetry Collector, Prometheus, and Grafana images.
- Persistent named volumes for every stateful component and one shared internal network.
- PostgreSQL bootstrap for four service-owned databases/users and pgvector in
  `knowledge_db`.
- A one-shot MinIO bootstrap container that creates the private `knowledge` bucket.
- Prometheus scraping for itself and the OpenTelemetry Collector.
- A provisioned Grafana Prometheus data source and infrastructure overview dashboard.
- Root `.env.example` with local-only defaults; the real `.env` remains gitignored.
- Compose validation in the existing CI verify job.
- `deploy/README.md` with start, stop, endpoint, credential, and reset instructions.

The five Spring Boot application images are intentionally not part of this Compose file.
DD-002 provides their dependencies; service runtime configuration and business migrations are
added with the corresponding service stories.

## Verification evidence

- `docker compose -f deploy/compose.yaml config --quiet` passed.
- PostgreSQL, RabbitMQ, Redis, MinIO, OpenTelemetry Collector, Prometheus, and Grafana
  reported `healthy`.
- `minio-init` completed once with exit code 0.
- PostgreSQL contained `identity_db`, `ticket_db`, `ai_db`, and `knowledge_db` with their
  four service roles.
- The `knowledge_db` pgvector extension reported version `0.8.1`.
- RabbitMQ authenticated the configured local account.
- MinIO contained the private `knowledge` bucket.
- Prometheus reported both `prometheus` and `otel-collector` scrape targets as `up`.
- Grafana returned HTTP 200 and contained the provisioned `Infrastructure Overview`
  dashboard.
- A PostgreSQL/Redis restart retained all databases, pgvector, and a temporary Redis test
  value. The temporary Redis key was removed after verification.
- The root Maven `clean verify` reactor remained successful after DD-002.
