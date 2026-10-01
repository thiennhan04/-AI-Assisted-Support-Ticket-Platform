# Ghi chú triển khai DD-202

## Thay đổi

- `POST /v1/tickets` giờ bắt buộc có UUID `Idempotency-Key`.
- Request đầu tiên lưu SHA-256 hash của create command và ticket ID trong 24 giờ.
- Gọi lại cùng key và content trả ticket ban đầu; content khác trả `409 IDEMPOTENCY_KEY_REUSED`.
- Response tạo, xem chi tiết và cập nhật có ETag như `"0"`.
- `PATCH /v1/tickets/{id}` yêu cầu giá trị đó trong `If-Match`. Thiếu header trả 428; version cũ trả `412 TICKET_VERSION_CONFLICT` kèm `currentVersion`.
- Flyway `V002` tạo `ticket.idempotency_record`. PostgreSQL transaction advisory lock làm các retry đồng thời với cùng tenant, requester, key chạy lần lượt.

## Đường review ngắn

1. Đọc ba test DD-202 trong `TicketCrudIT`: replay, key reuse và `If-Match` cũ.
2. Đọc `TicketController` để thấy hai HTTP header và ETag response.
3. Đọc `TicketCommandService.create` và `update` để hiểu đầy đủ business flow.
4. Đọc `TicketCreationIdempotencyRepository` để hiểu persistence contract nhỏ.
5. Đọc `JpaTicketCreationIdempotencyRepositoryAdapter`, Spring Data repository tương ứng và Flyway `V002` để hiểu chi tiết persistence.

Không thêm domain abstraction mới vì idempotency và HTTP precondition bảo vệ command; chúng không đổi lifecycle rule của Ticket.

## Kiểm tra

`./mvnw.cmd -pl services/ticket-service -am clean verify` thành công với 10 unit/security test và 11 PostgreSQL Testcontainers integration test. Formatter, compiler, Enforcer, Flyway V001/V002, đóng gói JAR, unit test và integration test đều hoàn tất thành công.
