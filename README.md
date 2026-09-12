# AI-Assisted Support Ticket Platform - Detail Design Package

Version: 1.0  
Target stack: Java 21, Spring Boot 3.x, PostgreSQL 16, pgvector, RabbitMQ, Redis, Docker Compose  
Audience: Backend developers, frontend developers, QA, DevOps and technical reviewers

## 1. Purpose

This package is an implementation-ready design for a support-ticket system where AI assists agents with classification, summarization, knowledge retrieval and reply drafting. AI never sends a reply automatically. The core ticket workflow remains available when the model provider is unavailable.

## 2. Service map

| Service | Responsibility | Port | Data owner |
|---|---|---:|---|
| Identity Service | Users, roles, login, refresh/revocation and JWT issuance | 8081 | `identity_db` |
| Ticket Service | Tickets, comments, attachments, status transitions, audit and AI result projection | 8082 | `ticket_db` |
| AI Orchestrator Service | Prompt orchestration, structured output, model-provider abstraction, AI jobs and feedback | 8083 | `ai_db` |
| Knowledge Service | Document ingestion, chunking, embeddings, vector search and citations | 8084 | `knowledge_db` + object storage |
| AI Worker | Consumes domain events and executes AI analysis/draft jobs | none | no business DB |

Shared infrastructure: RabbitMQ, Redis, S3-compatible object storage, OpenTelemetry Collector, Prometheus and Grafana.

## 3. Recommended implementation order

1. Start infrastructure with `ops/local-development.md`.
2. Implement Identity Service and validate JWT locally.
3. Implement Ticket Service CRUD and status state machine.
4. Add transactional outbox and RabbitMQ publishing.
5. Implement Knowledge Service ingestion and search.
6. Implement AI Orchestrator using a fake provider first.
7. Implement AI Worker and end-to-end event flow.
8. Replace the fake provider with a real LLM adapter.
9. Add metrics, security controls and evaluation tests.

## 4. Definition of done for the platform

- A user can create and view a ticket according to tenant and role permissions.
- A newly created ticket is analyzed asynchronously without blocking ticket creation.
- AI classification, summary and priority suggestion are stored with prompt/model versions.
- An agent can request a reply draft grounded in approved knowledge documents.
- Every grounded response includes citations that resolve to a document and chunk.
- AI/provider failure never prevents core ticket CRUD.
- Duplicate events do not create duplicate analyses or state transitions.
- All APIs expose correlation IDs and standardized problem responses.
- Integration tests run using Testcontainers.
- Secrets are not stored in source control.

## 5. Document index

- `00-overview/architecture.md`: boundaries, deployment and communication rules.
- `00-overview/domain-and-flows.md`: domain model, state machines and sequence flows.
- `00-overview/non-functional-requirements.md`: measurable quality requirements.
- `services/*.md`: detailed class-level design for each service.
- `contracts/openapi.yaml`: external API contract.
- `contracts/events.md`: RabbitMQ event contracts and delivery semantics.
- `database/ddl.sql`: reference DDL for all service-owned schemas.
- `database/data-dictionary.md`: table meaning, retention and ownership.
- `security/security-design.md`: authentication, authorization and AI threats.
- `ops/*.md`: configuration, local runtime, deployment and observability.
- `quality/test-strategy.md`: unit, integration, contract, evaluation and performance tests.
- `implementation/backlog.md`: implementation epics and acceptance criteria.
- `implementation/coding-conventions.md`: package structure and coding rules.

## 6. Explicit assumptions

- The first release supports multiple tenants but uses a shared database per service with `tenant_id` row isolation.
- JWT uses RS256. Identity Service owns the private key; other services use the public key/JWKS.
- RabbitMQ delivery is at-least-once. Consumers must be idempotent.
- PostgreSQL is used per service. Separate schemas are acceptable for local development; production should use separate logical databases/credentials.
- Knowledge files are stored in S3-compatible storage; metadata and vectors are stored in PostgreSQL.
- A model provider can be swapped without changing domain services.
- The frontend is out of scope except for required API behavior.


DD-001 – Bootstrap monorepo: root build và năm Spring Boot module.
DD-002 – Local infrastructure: PostgreSQL với bốn database/user riêng, RabbitMQ, Redis, MinIO, OpenTelemetry, Prometheus và Grafana.
Identity Service: tenant, user, role, login, JWT RS256, JWKS và refresh rotation.
Ticket Service: CRUD, phân quyền tenant, state machine, optimistic locking và audit.
Transactional outbox + event contracts.
Knowledge Service: upload, extraction, chunking và fake embedding.
AI Orchestrator: bắt đầu bằng deterministic fake provider.
AI Worker: nối luồng event end-to-end, retry và DLQ.
Sau khi luồng fake chạy ổn mới tích hợp model/embedding provider thật.
Cuối cùng hoàn thiện security hardening, observability, evaluation và performance tests.


| Module | Package/Application | Dependency nền |
|---|---|---|
| Identity | `com.portfolio.identity.IdentityServiceApplication` | Web, Validation, Security, OAuth2 Resource Server/JOSE, JPA, Flyway, PostgreSQL, AMQP, Actuator |
| Ticket | `com.portfolio.ticket.TicketServiceApplication` | Web, Validation, Security, OAuth2 Resource Server, JPA, Flyway, PostgreSQL, AMQP, Actuator |
| AI Orchestrator | `com.portfolio.ai.AiOrchestratorApplication` | Web, Validation, Security, JPA, Flyway, PostgreSQL, AMQP, Redis, Actuator |
| Knowledge | `com.portfolio.knowledge.KnowledgeServiceApplication` | Web, Validation, Security, OAuth2 Resource Server, JPA, Flyway, PostgreSQL, AMQP, Actuator |
| AI Worker | `com.portfolio.worker.AiWorkerApplication` | Spring Boot core, Spring Web client, AMQP, Redis, Actuator |


6. Thứ tự để dev bắt đầu DD-101
1. Chốt các điểm contract ở trên, đặc biệt cách xác định tenant.
2. Tạo application-local.yml kết nối identity_db.
3. Viết V001__baseline.sql cho schema Identity.
4. Viết domain và persistence adapter, kiểm thử truy vấn theo tenant.
5. Cấu hình password encoder, RSA keys, issuer, audience và TTL.
6. Viết luồng login, cấp phiên ban đầu và JWKS endpoint.
7. Viết controller/DTO/error response theo OpenAPI.
8. Thêm seed user bằng script/profile local.
9. Kiểm thử login thành công, lỗi chung, tài khoản bị khóa, tenant bị suspended, claims/chữ ký và xử lý lỗi tạo token.
