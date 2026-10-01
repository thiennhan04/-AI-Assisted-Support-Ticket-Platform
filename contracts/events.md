# Event contract

## 1. Envelope

Mọi event dùng JSON UTF-8 và envelope sau:

```json
{
  "eventId": "0191...uuid",
  "eventType": "ticket.created.v1",
  "occurredAt": "2026-08-24T10:15:30Z",
  "producer": "ticket-service",
  "correlationId": "uuid-or-trace-derived",
  "causationId": "source-event-id-or-null",
  "tenantId": "uuid",
  "subjectId": "uuid",
  "schemaVersion": 1,
  "data": {}
}
```

Quy tắc:

- `eventId` duy nhất toàn hệ thống và không đổi khi retry.
- Consumer bỏ qua field bổ sung mà nó không biết.
- Thay đổi payload gây breaking change phải dùng hậu tố event type mới.
- Timestamp dùng UTC ISO-8601.
- Giảm thiểu PII; không bao giờ nhúng attachment hoặc secret.
- Bắt buộc có broker persistence, publisher confirm và durable queue.

## 2. Exchange và routing

Topic exchange: `platform.domain.x`.

| Event type/routing key | Producer | Consumer chính |
|---|---|---|
| `ticket.created.v1` | Ticket | AI Worker |
| `ticket.analysis.requested.v1` | Ticket | AI Worker |
| `ticket.draft.requested.v1` | Ticket | AI Worker |
| `ai.analysis.completed.v1` | AI Orchestrator | Ticket |
| `ai.analysis.failed.v1` | AI Orchestrator | Ticket |
| `ai.draft.completed.v1` | AI Orchestrator | Ticket |
| `ai.draft.failed.v1` | AI Orchestrator | Ticket |
| `ai.feedback.submitted.v1` | Ticket | AI Orchestrator |
| `knowledge.ingest.requested.v1` | Knowledge | AI Worker |
| `knowledge.document.activated.v1` | Knowledge | audit/metrics |

## 3. Định nghĩa payload

### `ticket.created.v1`

Schema: [`event-schemas/ticket.created.v1.schema.json`](event-schemas/ticket.created.v1.schema.json).
Event type và RabbitMQ routing key đều là `ticket.created.v1`.

```json
{
  "ticketId": "uuid",
  "ticketNumber": "SUP-00001234",
  "requesterId": "uuid",
  "subject": "Cannot reset password",
  "description": "...",
  "createdAt": "2026-08-24T10:15:30Z",
  "contentVersion": 0
}
```

### `ticket.draft.requested.v1`

```json
{
  "jobId": "uuid",
  "ticketId": "uuid",
  "requestedBy": "uuid",
  "locale": "en",
  "tone": "PROFESSIONAL",
  "ticketSnapshot": {
    "subject": "...",
    "description": "...",
    "latestComments": [{"authorType": "CUSTOMER", "body": "..."}],
    "contentVersion": 4
  }
}
```

### `ai.analysis.completed.v1`

```json
{
  "jobId": "uuid",
  "ticketId": "uuid",
  "inputContentVersion": 0,
  "summary": "Customer cannot reset the account password.",
  "suggestedCategory": "ACCOUNT",
  "suggestedPriority": "MEDIUM",
  "rationale": "Login access is blocked but no security incident is indicated.",
  "promptVersion": "ticket-analysis/1.0.0",
  "model": "configured-model-alias",
  "generatedAt": "2026-08-24T10:15:35Z"
}
```

Ticket Service đánh dấu kết quả là stale khi `inputContentVersion` cũ hơn content version hiện tại. Service có thể hiển thị kết quả nhưng không được tự động áp dụng suggestion.

### `ai.draft.completed.v1`

```json
{
  "jobId": "uuid",
  "ticketId": "uuid",
  "inputContentVersion": 4,
  "reply": "Please follow the password reset guide...",
  "citations": [
    {
      "documentId": "uuid",
      "chunkId": "uuid",
      "label": "Password Reset Guide, page 2",
      "excerpt": "Open Account Settings and select Reset Password..."
    }
  ],
  "riskFlags": [],
  "promptVersion": "draft-reply/1.0.0",
  "model": "configured-model-alias",
  "generatedAt": "2026-08-24T10:16:00Z"
}
```

### Failure event

Các field: `jobId`, `ticketId`, `errorCode`, `retryable`, `attemptCount`, `failedAt`. Không bao giờ chứa raw error body từ provider vì nó có thể chứa prompt hoặc nội dung người dùng.

## 4. Ngữ nghĩa giao nhận

- Giao nhận at-least-once.
- Producer ghi outbox cùng domain transaction.
- Publisher chỉ đánh dấu row sau broker confirmation.
- Consumer áp dụng business change và `processed_event` marker một cách nguyên tử.
- Không giả định thứ tự message trên toàn hệ thống. State/version check xử lý việc đảo thứ tự.
- Poison message chuyển vào DLQ sau số lần thử đã cấu hình.

### Vòng đời outbox của Ticket Service

1. Ticket Service thêm `ticket.created.v1` vào `ticket.outbox_event` trong cùng database transaction tạo ticket.
2. Background publisher claim row sẵn sàng bằng `FOR UPDATE SKIP LOCKED`, commit claim ngắn rồi publish bên ngoài ticket transaction.
3. Row chỉ thành `PUBLISHED` sau khi RabbitMQ xác nhận persistent message và không trả về do unroutable.
4. Publish bị reject, unroutable, timeout hoặc thất bại sẽ trả row về `PENDING` với exponential backoff. Claim `PROCESSING` cũ đủ điều kiện xử lý lại sau claim timeout.
5. Nếu crash sau khi RabbitMQ nhận message nhưng trước database update, event có thể được publish lại. Consumer vì thế phải deduplicate theo `eventId` bất biến.

Trong DD-204, tạo ticket là producer action duy nhất đã triển khai. Event yêu cầu analysis/draft được thêm cùng API tương ứng thay vì tự nghĩ ra update/comment event chưa có tài liệu. Trước khi DD-501 tạo và bind durable consumer queue, unroutable event vẫn nằm trong outbox thay vì bị đánh dấu đã giao.

## 5. Tương thích contract

- Thêm event JSON schema dưới `contracts/event-schemas/` khi bắt đầu code.
- Producer chạy schema validation trong unit test.
- Consumer duy trì fixture cho phiên bản hiện tại và phiên bản trước còn hỗ trợ.
- PR đổi contract cần owner của cả producer và consumer.
