# Theo dõi task triển khai

Cập nhật lần cuối: 2026-10-01

Milestone tiếp theo: **DD-301 - Luồng upload**

Hoàn thành: **9/24 task (38%)**

File này theo dõi trạng thái bàn giao. Xem `backlog.md` để biết yêu cầu và acceptance criteria gốc; không thay thế các yêu cầu đó bằng bản tóm tắt này.

## Chú thích trạng thái

- `[x]` Đã hoàn thành, kiểm thử, viết tài liệu, commit và push.
- `[~]` Đang thực hiện nhưng chưa sẵn sàng commit.
- `[ ]` Chưa bắt đầu.
- `[!]` Bị chặn hoặc cần quyết định.

## Task vừa hoàn thành

### DD-204 - Transactional outbox

Trạng thái: `[x]` Đã hoàn thành

- [x] Xác nhận event name, routing key và payload version theo `contracts/events.md`.
- [x] Thêm Flyway `V004` cho `outbox_event`.
- [x] Ghi outbox row trong cùng transaction với ticket mutation.
- [x] Publish pending row tới RabbitMQ bên ngoài business transaction.
- [x] Dùng publisher confirm trước khi đánh dấu row đã publish.
- [x] Giữ failed row có thể retry và an toàn qua service restart.
- [x] Test publish failure, retry và kỳ vọng duplicate delivery.
- [x] Cập nhật event documentation và tạo ghi chú triển khai DD-204; không cần đổi REST.
- [x] Chạy `clean verify` cho Ticket Service.
- [x] Commit và push DD-204 (`4259c81`).

Definition of done: broker failure mô phỏng để lại outbox row có thể retry và restart publisher không làm mất event.

## Epic 0 - Repository và hạ tầng

| Trạng thái | Task | Kết quả | Commit |
|---|---|---|---|
| [x] | DD-001 Khởi tạo monorepo | Maven root, năm service, hai thư viện, Java 21 và chất lượng build | `3f6c9fd` |
| [x] | DD-002 Hạ tầng local | PostgreSQL, RabbitMQ, Redis, MinIO, OTel, Prometheus và Grafana | `3f6c9fd` |

Tiến độ: **2/2**

## Epic 1 - Identity

| Trạng thái | Task | Kết quả | Commit |
|---|---|---|---|
| [x] | DD-101 Login và JWT | Login tenant/user, RS256 JWT và JWKS | `964fbb5` |
| [x] | DD-102 Refresh rotation | Token-family rotation, logout và phát hiện reuse | `c03d433` |
| [x] | DD-103 Tích hợp resource server | Xác minh JWT và ánh xạ principal trong resource service | `67783ef` |

Tiến độ: **3/3**

## Epic 2 - Ticket core

| Trạng thái | Task | Kết quả | Commit |
|---|---|---|---|
| [x] | DD-201 Ticket CRUD/query | CRUD an toàn theo tenant, phân quyền, filter và lifecycle | `4486560` |
| [x] | DD-202 Optimistic locking/idempotency | ETag/If-Match và Idempotency-Key | `ad0cbc6` |
| [x] | DD-203 Comment và audit | Public/internal comment và append-only audit | `81b8e3f` |
| [x] | DD-204 Transactional outbox | Publish RabbitMQ đáng tin cậy | `4259c81` |

Tiến độ: **4/4**

## Epic 3 - Knowledge

| Trạng thái | Task | Kết quả dự kiến | Commit |
|---|---|---|---|
| [ ] | DD-301 Luồng upload | Metadata, signed upload và xác minh hoàn tất | - |
| [ ] | DD-302 Trích xuất và chia chunk | Trích xuất PDF/DOCX/TXT và chunk xác định | - |
| [ ] | DD-303 Embedding và vector search | Fake/real embedding và pgvector search an toàn theo tenant | - |
| [ ] | DD-304 Thay thế/xóa phiên bản | Chuyển active version nguyên tử và loại bỏ tức thời | - |

Tiến độ: **0/4**

## Epic 4 - AI orchestration

| Trạng thái | Task | Kết quả dự kiến | Commit |
|---|---|---|---|
| [ ] | DD-401 Vòng đời AI job | Job idempotent, input hash và state machine | - |
| [ ] | DD-402 Phân tích ticket | Prompt có version và structured output đã kiểm tra | - |
| [ ] | DD-403 Bản nháp dựa trên tri thức | Knowledge retrieval và citation đã xác minh | - |
| [ ] | DD-404 Khả năng phục hồi/chi phí provider | Timeout, retry, circuit breaker và budget | - |

Tiến độ: **0/4**

## Epic 5 - Worker và projection

| Trạng thái | Task | Kết quả dự kiến | Commit |
|---|---|---|---|
| [ ] | DD-501 Event consumer/retry/DLQ | Consumer idempotent, lịch retry và DLQ | - |
| [ ] | DD-502 Ticket AI projection | Projection analysis/draft idempotent | - |
| [ ] | DD-503 Phê duyệt draft/feedback | Comment được con người duyệt và feedback event | - |

Tiến độ: **0/3**

## Epic 6 - Chất lượng và vận hành

| Trạng thái | Task | Kết quả dự kiến | Commit |
|---|---|---|---|
| [ ] | DD-601 Khả năng quan sát | Trace xuyên service, metric, dashboard và alert | - |
| [ ] | DD-602 Tăng cường bảo mật | Rate limit, scanning, PII control và threat fixture | - |
| [ ] | DD-603 Đánh giá/hiệu năng | Quality dataset, load test và release gate đã đo | - |
| [ ] | DD-604 Demo/tài liệu | Seed data và demo end-to-end tái hiện được | - |

Tiến độ: **0/4**

## Thứ tự bàn giao dự kiến

```text
DD-301 -> DD-302 -> DD-303 -> DD-304
  -> DD-401 -> DD-402 -> DD-403 -> DD-404
  -> DD-501 -> DD-502 -> DD-503
  -> DD-601 -> DD-602 -> DD-603 -> DD-604
```

## Hạng mục thiết kế chưa có mã DD

Các hạng mục này có trong thiết kế nhưng chưa là task riêng trong `backlog.md`. Cần lên lịch rõ ràng thay vì âm thầm thêm vào DD khác.

| Trạng thái | Hạng mục | Thời điểm đề xuất |
|---|---|---|
| [ ] | Upload/download attachment của ticket qua MinIO | Sau khi pattern upload của Knowledge ổn định |
| [ ] | Xác minh assignee là agent active cùng tenant | Sau khi có contract Identity user-directory |
| [ ] | Web UI cho người dùng cuối | Epic frontend riêng sau khi backend API ổn định |

## Quy tắc cập nhật

Khi bắt đầu task:

1. Đổi trạng thái thành `[~]`.
2. Đưa task vào phần **Task hiện tại** cùng checklist triển khai.

Khi hoàn tất task:

1. Xác nhận acceptance test thành công.
2. Thêm hoặc cập nhật implementation note.
3. Commit và push code.
4. Đổi trạng thái thành `[x]`, thêm commit hash và cập nhật tổng tiến độ.

Chỉ dùng `[!]` khi không thể tiếp tục nếu thiếu quyết định hoặc external dependency, đồng thời ghi lý do chặn ngay cạnh task.
