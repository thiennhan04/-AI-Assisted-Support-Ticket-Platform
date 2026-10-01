# Ghi chú triển khai DD-204

## Hành vi đã bàn giao

- Khi tạo ticket, một event `ticket.created.v1` được lưu vào `ticket.outbox_event` trong cùng PostgreSQL transaction với ticket, audit row và idempotency record.
- Gọi lại cùng `Idempotency-Key` trả ticket hiện có và không tạo outbox event thứ hai.
- Background publisher claim một batch nhỏ bằng `FOR UPDATE SKIP LOCKED`, cho phép nhiều Ticket Service instance cùng publish mà thông thường không chọn trùng row.
- Publish diễn ra sau ticket transaction. Message là persistent và gửi tới durable topic exchange `platform.domain.x` với routing key `ticket.created.v1`.
- Row chỉ được đánh dấu `PUBLISHED` sau positive RabbitMQ publisher confirm. Negative confirm, timeout, mất kết nối và unroutable message vẫn có thể retry.
- Process crash không làm row mắc kẹt mãi: row `PROCESSING` cũ được reclaim sau claim timeout. Khoảng thời gian có thể giao trùng là có chủ đích và phù hợp contract at-least-once.

DD-204 không đổi REST API nên `contracts/openapi.yaml` giữ nguyên. Event envelope, payload, routing key, schema và delivery lifecycle được mô tả tại `contracts/events.md` và `contracts/event-schemas/ticket.created.v1.schema.json`.

## Lý do các class tồn tại

- `TicketEventOutbox` là port nhỏ hướng tới application. `TicketCommandService` chỉ yêu cầu lưu event tạo ticket; nó không biết JDBC hoặc RabbitMQ.
- `JpaTicketOutboxAdapter` serialize versioned event contract và giao persistence cho `OutboxEventSpringDataRepository`.
- `TicketOutboxPublisher` chứa flow claim, send, xử lý confirm và retry dễ đọc. Seam `OutboxEventSender` lồng bên trong cho phép test recovery mà không cần broker thật.
- `RabbitOutboxSenderAdapter` chỉ chứa message header riêng cho RabbitMQ và logic publisher-confirm.
- `OutboxConfiguration` và `OutboxProperties` chứa exchange cùng các giá trị tinh chỉnh vận hành.

Các trách nhiệm này được giữ cùng nhau trong package `infrastructure.messaging`; không tạo service, mapper, exception hierarchy hoặc status enum riêng cho một workflow này.

## Luồng lỗi và restart

```text
create ticket transaction
  -> ticket + audit + idempotency + PENDING outbox row commit together

publisher poll
  -> claim row as PROCESSING
  -> send persistent RabbitMQ message
     -> confirmed and routed: mark PUBLISHED
     -> failed/rejected/unroutable: return to PENDING with backoff

process stops while PROCESSING
  -> next instance reclaims the stale row after claim-timeout
```

Publisher có thể gửi cùng immutable `eventId` nhiều hơn một lần nếu crash sau broker confirmation nhưng trước `markPublished`. Consumer DD-501 phải lưu processed-event marker trong business transaction của chính nó để bỏ qua bản trùng an toàn.

## Đường review ngắn

1. Đọc `TicketOutboxRecoveryIT.retriesEventAfterPublisherRestart` để hiểu hành vi lỗi/restart.
2. Đọc create flow trong `TicketCommandService.create`.
3. Đọc `TicketEventOutbox`, sau đó `JpaTicketOutboxAdapter.appendTicketCreated`, Spring Data repository tương ứng và Flyway `V004`.
4. Đọc `TicketOutboxPublisher.publishPending`.
5. Đọc `RabbitOutboxSenderAdapter.send` để hiểu persistent delivery, mandatory routing và broker confirm.
6. Đọc `contracts/events.md` và JSON Schema cho external contract.

## Cấu hình vận hành

Giá trị mặc định cố ý thận trọng và có thể ghi đè bằng biến môi trường:

- `OUTBOX_PUBLISHER_ENABLED=true`
- `OUTBOX_POLL_INTERVAL_MS=1000`
- `OUTBOX_BATCH_SIZE=20`
- `OUTBOX_CLAIM_TIMEOUT=30s`
- `OUTBOX_CONFIRM_TIMEOUT=5s`
- `OUTBOX_RETRY_MAX_DELAY=60s`

`eventId` được dùng làm correlation ID cho đến khi DD-601 thêm end-to-end correlation context. Ticket subject/description chỉ xuất hiện vì versioned analysis event yêu cầu; log không bao giờ chứa payload.

## Kiểm tra

`./mvnw.cmd -pl services/ticket-service -am clean verify` thành công với 13 unit/security test và 14 PostgreSQL Testcontainers integration test. Flyway áp dụng thành công V001 đến V004. Recovery test mô phỏng broker send thất bại, xác minh row đã lưu trở về `PENDING`, sau đó tạo publisher instance mới để reclaim và hoàn tất cùng event.
