# Nền tảng hỗ trợ xử lý ticket bằng AI - Bộ thiết kế chi tiết

Phiên bản: 1.0

Tech stack mục tiêu: Java 21, Spring Boot 3.x, PostgreSQL 16, pgvector, RabbitMQ, Redis, Docker Compose

Đối tượng đọc: Backend developer, frontend developer, QA, DevOps và technical reviewer

## 1. Mục đích

Đây là thiết kế sẵn sàng triển khai cho hệ thống ticket hỗ trợ, trong đó AI giúp agent phân loại, tóm tắt, truy xuất tri thức và soạn câu trả lời. AI không bao giờ tự động gửi câu trả lời. Quy trình ticket cốt lõi vẫn hoạt động khi model provider không khả dụng.

## 2. Bản đồ service

| Service | Trách nhiệm | Port | Nơi sở hữu dữ liệu |
|---|---|---:|---|
| Identity Service | User, role, login, refresh/revocation và phát JWT | 8081 | `identity_db` |
| Ticket Service | Ticket, comment, attachment, chuyển trạng thái, audit và projection kết quả AI | 8082 | `ticket_db` |
| AI Orchestrator Service | Điều phối prompt, structured output, trừu tượng model provider, AI job và feedback | 8083 | `ai_db` |
| Knowledge Service | Nhập tài liệu, chia chunk, embedding, vector search và citation | 8084 | `knowledge_db` + object storage |
| AI Worker | Consume domain event và chạy job AI analysis/draft | không có | không có business DB |

Hạ tầng dùng chung: RabbitMQ, Redis, S3-compatible object storage, OpenTelemetry Collector, Prometheus và Grafana.

## 3. Thứ tự triển khai đề xuất

1. Khởi động hạ tầng theo `ops/local-development.md`.
2. Triển khai Identity Service và xác minh JWT ở local.
3. Triển khai Ticket Service CRUD và status state machine.
4. Thêm transactional outbox và publish RabbitMQ.
5. Triển khai ingestion và search của Knowledge Service.
6. Triển khai AI Orchestrator với fake provider trước.
7. Triển khai AI Worker và luồng event end-to-end.
8. Thay fake provider bằng adapter LLM thật.
9. Thêm metric, security control và evaluation test.

## 4. Definition of done của platform

- Người dùng có thể tạo và xem ticket theo quyền tenant/role.
- Ticket mới được phân tích bất đồng bộ mà không chặn thao tác tạo.
- Phân loại, tóm tắt và gợi ý priority của AI được lưu cùng prompt/model version.
- Agent có thể yêu cầu bản nháp câu trả lời dựa trên tài liệu tri thức được duyệt.
- Mọi grounded response có citation trỏ được tới document và chunk.
- Lỗi AI/provider không bao giờ ngăn Ticket CRUD cốt lõi.
- Event trùng không tạo analysis hoặc state transition trùng.
- Mọi API cung cấp correlation ID và problem response chuẩn hóa.
- Integration test chạy bằng Testcontainers.
- Secret không được lưu trong source control.

## 5. Mục lục tài liệu

- `00-overview/architecture.md`: ranh giới, deployment và quy tắc giao tiếp.
- `00-overview/domain-and-flows.md`: domain model, state machine và sequence flow.
- `00-overview/non-functional-requirements.md`: yêu cầu chất lượng đo được.
- `services/*.md`: thiết kế chi tiết đến cấp class cho từng service.
- `contracts/openapi.yaml`: external API contract.
- `contracts/events.md`: RabbitMQ event contract và delivery semantics.
- `database/ddl.sql`: DDL tham chiếu cho mọi schema do service sở hữu.
- `database/data-dictionary.md`: ý nghĩa table, retention và quyền sở hữu.
- `security/security-design.md`: authentication, authorization và mối đe dọa AI.
- `ops/*.md`: cấu hình, local runtime, deployment và observability.
- `quality/test-strategy.md`: unit, integration, contract, evaluation và performance test.
- `implementation/backlog.md`: epic triển khai và acceptance criteria.
- `implementation/task-tracker.md`: trạng thái bàn giao, commit và checklist task tiếp theo.
- `implementation/coding-conventions.md`: cấu trúc package và quy tắc code.

## 6. Giả định rõ ràng

- Phiên bản đầu hỗ trợ nhiều tenant nhưng dùng database dùng chung theo service, cô lập row bằng `tenant_id`.
- JWT dùng RS256. Identity Service sở hữu private key; service khác dùng public key/JWKS.
- RabbitMQ giao nhận at-least-once. Consumer phải idempotent.
- Mỗi service dùng PostgreSQL riêng. Local có thể dùng schema riêng; production nên dùng logical database/credential riêng.
- File tri thức lưu trong S3-compatible storage; metadata và vector lưu trong PostgreSQL.
- Có thể thay model provider mà không đổi domain service.
- Frontend nằm ngoài phạm vi, ngoại trừ hành vi API cần thiết.

## 7. Các mốc triển khai chính

1. DD-001 - Khởi tạo monorepo: root build và năm Spring Boot module.
2. DD-002 - Hạ tầng local: PostgreSQL với bốn database/user riêng, RabbitMQ, Redis, MinIO, OpenTelemetry, Prometheus và Grafana.
3. Identity Service: tenant, user, role, login, JWT RS256, JWKS và refresh rotation.
4. Ticket Service: CRUD, phân quyền tenant, state machine, optimistic locking và audit.
5. Transactional outbox và event contract.
6. Knowledge Service: upload, extraction, chunking và fake embedding.
7. AI Orchestrator: bắt đầu bằng deterministic fake provider.
8. AI Worker: nối luồng event end-to-end, retry và DLQ.
9. Chỉ tích hợp model/embedding provider thật sau khi luồng fake ổn định.
10. Cuối cùng hoàn thiện security hardening, observability, evaluation và performance test.

## 8. Module bootstrap

| Module | Package/Application | Dependency nền |
|---|---|---|
| Identity | `com.portfolio.identity.IdentityServiceApplication` | Web, Validation, Security, OAuth2 Resource Server/JOSE, JPA, Flyway, PostgreSQL, AMQP, Actuator |
| Ticket | `com.portfolio.ticket.TicketServiceApplication` | Web, Validation, Security, OAuth2 Resource Server, JPA, Flyway, PostgreSQL, AMQP, Actuator |
| AI Orchestrator | `com.portfolio.ai.AiOrchestratorApplication` | Web, Validation, Security, JPA, Flyway, PostgreSQL, AMQP, Redis, Actuator |
| Knowledge | `com.portfolio.knowledge.KnowledgeServiceApplication` | Web, Validation, Security, OAuth2 Resource Server, JPA, Flyway, PostgreSQL, AMQP, Actuator |
| AI Worker | `com.portfolio.worker.AiWorkerApplication` | Spring Boot core, Spring Web client, AMQP, Redis, Actuator |

## 9. Thứ tự để developer bắt đầu DD-101

1. Chốt các điểm contract, đặc biệt cách xác định tenant.
2. Tạo `application-local.yml` kết nối `identity_db`.
3. Viết `V001__baseline.sql` cho schema Identity.
4. Viết domain và persistence adapter, kiểm thử truy vấn theo tenant.
5. Cấu hình password encoder, RSA key, issuer, audience và TTL.
6. Viết luồng login, cấp phiên ban đầu và JWKS endpoint.
7. Viết controller/DTO/error response theo OpenAPI.
8. Thêm seed user bằng script/profile local.
9. Kiểm thử login thành công, lỗi chung, tài khoản bị khóa, tenant suspended, claim/chữ ký và xử lý lỗi tạo token.
