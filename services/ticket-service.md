# Thiết kế chi tiết Ticket Service

## 1. Trách nhiệm

- Vòng đời ticket, assignment, comment và attachment metadata.
- Phân quyền, cô lập tenant, audit log và optimistic concurrency.
- Transactional outbox.
- Bản chiếu AI analysis/draft để UI đọc.

## 2. Cấu trúc package

```text
com.portfolio.ticket
  api
    TicketController
    CommentController
    AiFeatureController
  application
    TicketCommandService
    TicketQueryService
    AiProjectionService
    AttachmentService
  domain
    Ticket
    TicketStatus
    TicketPolicy
    TicketRepository
  infrastructure
    persistence
    messaging
    storage
    security
```

### Caller đã xác thực

Ticket Service là OAuth2 Resource Server, chỉ nhận RS256 access token do Identity Service cấu hình
phát hành. Service lấy signing key từ `JWKS_URI`, kiểm tra `iss`, `aud`, `exp`, `sub`, `tid`, `roles`
rồi chuyển claim thành `AuthenticatedPrincipal`. Application luôn lấy tenant/user từ principal,
không lấy từ request. Controller dùng authority `ROLE_CUSTOMER`, `ROLE_AGENT`, `ROLE_ADMIN`.

Token người dùng bị từ chối tại `/internal/**`; service identity sẽ được bổ sung cùng internal API.

## 3. Domain command

| Command | Role được phép | Kiểm tra chính |
|---|---|---|
| CreateTicket | đã xác thực | requester là caller, trừ Agent/Admin |
| UpdateContent | requester khi OPEN, Agent/Admin | cần expected version |
| AssignTicket | Agent/Admin | assignee là active agent cùng tenant |
| ChangeStatus | tùy transition | state-machine rule |
| AddComment | participant/Admin | không rỗng, <= 10.000 ký tự |
| RequestAiDraft | Agent/Admin | ticket chưa CLOSED, đủ nội dung |
| ApproveAiDraft | Agent/Admin | draft tồn tại và chưa thay đổi |

## 4. Hành vi API

### Tạo ticket

`POST /v1/tickets` yêu cầu UUID `Idempotency-Key`. Request hash và response reference được lưu 24
giờ. Dùng lại key với body khác trả `409 IDEMPOTENCY_KEY_REUSED`. Ticket number gồm prefix theo tenant
và sequence zero-padded như `SUP-00001234`; UUID vẫn là ID chuẩn.

### Truy vấn

`GET /v1/tickets` hỗ trợ `status`, `priority`, `category`, `assigneeId`, `requesterId`, `createdFrom`,
`createdTo`, `q`, `page`, `size`, `sort`; size tối đa 50. CUSTOMER luôn chỉ thấy ticket của mình.

### Cập nhật

Mọi mutable command nhận `If-Match: "<version>"`. Thiếu header trả `428 PRECONDITION_REQUIRED`;
version cũ trả `412 TICKET_VERSION_CONFLICT` kèm current version.

### Endpoint AI

- `POST /v1/tickets/{id}/ai-analysis/retry` → `202`.
- `POST /v1/tickets/{id}/ai-drafts` → `202 { jobId, status }`.
- `GET /v1/tickets/{id}/ai-drafts/{draftId}`.
- `POST /v1/tickets/{id}/ai-drafts/{draftId}/approve` tạo comment thường từ nội dung có thể sửa.
- `POST /v1/tickets/{id}/ai-feedback` ghi rating/reason qua event.

## 5. Persistence và index

Bảng chính: `ticket`, `ticket_comment`, `ticket_attachment`, `ticket_ai_analysis`, `ticket_ai_draft`,
`ticket_audit`, `idempotency_record`, `outbox_event`, `processed_event`.

Index bắt buộc: `(tenant_id, status, updated_at desc)`, `(tenant_id, assignee_id, status)`,
`(tenant_id, requester_id, created_at desc)`, GIN full-text trên subject/description và unique
`(tenant_id, ticket_number)`.

## 6. Chiếu event

Khi nhận `ai.analysis.completed.v1`:

1. Chèn `processed_event`, kết thúc thành công nếu trùng.
2. Xác nhận tenant và ticket tồn tại.
3. Upsert analysis theo `ai_job_id`.
4. Không ghi đè category/priority đã chấp nhận.
5. Thêm audit `AI_ANALYSIS_RECEIVED`.

Draft hoàn tất được upsert và trở thành bất biến; nội dung người dùng sửa chỉ đi qua lệnh approve để
tạo comment.

## 7. Luồng attachment

1. `POST /v1/tickets/{id}/attachments/uploads` tạo metadata `PENDING` và signed PUT URL.
2. Client upload object.
3. `POST .../{attachmentId}/complete` xác minh size/checksum/content type rồi chuyển `ACTIVE`.
4. Malware scan có thể giữ `SCANNING`; object chưa scan không được download.

## 8. Mã lỗi

- `TICKET_NOT_FOUND`: 404 kể cả cross-tenant để tránh enumeration.
- `TICKET_INVALID_TRANSITION`: 409.
- `TICKET_VERSION_CONFLICT`: 412.
- `TICKET_FORBIDDEN`: 403 khi có thể an toàn tiết lộ resource tồn tại.
- `AI_DRAFT_NOT_READY`: 409.

## 9. Kiểm thử

- Mọi transition hợp lệ/không hợp lệ và ma trận role/tenant.
- Optimistic locking khi cập nhật đồng thời.
- Idempotent create và replay khác nội dung.
- Outbox nguyên tử với ticket; bỏ qua AI event trùng.
- Lỗi AI không làm đổi ticket status.

## 10. Trạng thái triển khai

DD-201 đã có create/detail/query/filter/pagination/update, state machine, role policy, PostgreSQL và
Flyway `V001`. Tenant/requester luôn lấy từ JWT principal.

DD-202 thêm idempotency 24 giờ theo tenant/requester, ETag, bắt buộc `If-Match` và
`412 TICKET_VERSION_CONFLICT`; `V002` lưu request hash và ticket gốc.

DD-203 thêm public/internal comment và audit append-only qua `V003`. Customer chỉ thấy public comment
của ticket mình; Agent/Admin thấy cả hai loại trong tenant. Mutation và audit dùng cùng transaction.

DD-204 thêm transactional outbox và RabbitMQ publisher confirm qua `V004`. Việc xác minh assignee là
active agent cùng tenant chờ Identity user-directory integration.
