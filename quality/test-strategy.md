# Chiến lược kiểm thử và đánh giá

## 1. Test pyramid

- Unit test: domain policy, state machine, validator output/prompt và chunking.
- Slice test: controller/security/repository.
- Integration test: PostgreSQL/RabbitMQ/Redis/MinIO thật qua Testcontainers.
- Contract test: OpenAPI và event schema.
- End-to-end: toàn nền tảng với fake AI và fake embedding.
- AI evaluation: dataset cố định với provider thật, chạy định kỳ/thủ công vì có chi phí.

## 2. Bộ test tối thiểu theo service

### Identity

- Claim, expiry và rotation của token.
- Xác minh password và rate limit.
- Refresh concurrency/reuse detection.
- Cô lập tenant cho Admin.

### Ticket

- Mọi tổ hợp role × action × ownership.
- State-machine transition.
- Optimistic locking và idempotency.
- Outbox atomicity và duplicate consumer event.

### AI Orchestrator

- Job idempotency và xung đột input hash.
- Strict schema validation và một lần repair.
- Citation subset validation.
- Phân loại lỗi provider, retry và circuit breaker.
- Audit version của prompt/model.

### Knowledge

- Fixture extraction/chunking.
- Tính nguyên tử khi activate version.
- Lọc ACL trước ranking.
- Fixture vector/hybrid ranking.
- Xóa phải làm mất khả năng search ngay.

### Worker

- Retry routing, DLQ và giữ nguyên ID.
- Cô lập concurrency pool.
- Reconciliation không tạo logical job mới.

## 3. Contract test

- Lint `openapi.yaml` và generate client trong CI để chứng minh contract sử dụng được.
- So API ngoài với main branch; breaking change phải fail nếu chưa version.
- Validate event sample theo JSON Schema.
- Consumer test dùng producer fixture cho version hiện tại và version trước còn hỗ trợ.

## 4. Dataset đánh giá AI

### Dataset phân loại ticket

Field CSV/JSONL: `caseId`, subject, description, expected category, expected priority range,
sensitive-data flag. Tối thiểu 100 case được review thủ công, chia 70 development/30 locked test.

Metric: Macro F1 cho category; exact/adjacent accuracy cho priority; structured-output validity;
sensitive-data redaction recall; p50/p95 latency và chi phí trung bình.

### Dataset RAG

Field: query, allowed document ID, supporting chunk/document mong đợi, unanswerable flag và
principal/role.

Metric: Recall@5, MRR, citation validity (cấu trúc phải 100%), groundedness theo rubric và human
review lấy mẫu, tỷ lệ refusal/no-knowledge đúng, số lần ACL leakage phải bằng 0.

## 5. Release gate

- Không có finding bảo mật critical/high.
- Migration chạy được từ DB sạch và snapshot release trước.
- Contract compatibility pass.
- Cross-tenant test matrix pass 100%.
- Citation structural validity 100% và không ACL leakage.
- Category Macro F1 mục tiêu >= 0,80.
- Retrieval Recall@5 mục tiêu >= 0,85 trên controlled corpus.
- Core ticket API p95 đạt NFR dưới baseline load.

Các mục tiêu là giả thuyết ban đầu. Phải ghi dataset và phương pháp đo thực tế trong README, không
được tuyên bố số liệu chưa đo.

## 6. Kịch bản hiệu năng

1. 100 user đồng thời xem danh sách ticket, 20 write/s trong 15 phút.
2. 20 AI job/s với fake provider có phân phối latency.
3. Backlog 10.000 event và khôi phục không mất/trùng logical change.
4. Search trên một triệu synthetic chunk.
5. 100 refresh request đồng thời cho cùng token; đúng một request thành công.

## 7. Quy tắc dữ liệu test

- Chỉ dùng dữ liệu tổng hợp; không dùng document thật của công ty/khách hàng.
- Seed tenant/user xác định và UUID ổn định cho integration test.
- Tách locked evaluation case khỏi dữ liệu dùng lặp prompt.
- Version dataset cùng thay đổi prompt/config.
