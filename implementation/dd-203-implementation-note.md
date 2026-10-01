# Ghi chú triển khai DD-203

## Hành vi đã bàn giao

- `POST /v1/tickets/{ticketId}/comments` thêm public/internal comment và trả ETag mới của ticket.
- `GET /v1/tickets/{ticketId}/comments` trả comment theo thứ tự thời gian.
- Customer chỉ có thể thêm/xem public comment trên ticket của chính mình.
- Agent/Admin có thể thêm/xem public và internal comment trong tenant.
- Ticket đã đóng từ chối comment mới cho đến khi Admin mở lại.
- Việc tạo ticket, thay đổi field thật sự, status change do assignment và thêm comment đều ghi append-only audit row trong cùng transaction với mutation.
- Content audit cố ý không lưu giá trị subject/description; audit chỉ nói content đã thay đổi mà không sao chép nội dung ticket nhạy cảm.

## Lý do các class tồn tại

- `TicketComment` biểu diễn một conversation entry bất biến.
- `TicketHistoryRepository` là persistence contract duy nhất cho comment và audit kiểu append-only.
- `JpaTicketHistoryRepositoryAdapter` ánh xạ comment/audit qua các Spring Data repository tương ứng.
- `TicketAuditRecorder` so sánh state ticket và chuyển thay đổi thật thành audit action. Nhờ đó `TicketCommandService` tập trung vào các flow create, update, add-comment dễ đọc.
- `AddCommentRequest` và `TicketCommentResponse` là transport shape. Không tạo thêm comment service hoặc command class chỉ có một method.

## Đường review ngắn

1. Đọc `TicketCrudIT.hidesInternalCommentsFromCustomers` và `auditsTicketMutations`.
2. Đọc hai comment endpoint trong `TicketController`.
3. Đọc `TicketCommandService.addComment` và `TicketQueryService.listComments`.
4. Đọc `TicketPolicy.requireCanAddInternalComment`.
5. Đọc `TicketAuditRecorder`, sau đó `JpaTicketHistoryRepositoryAdapter` và Flyway `V003`.

## Kiểm tra

`./mvnw.cmd -pl services/ticket-service -am clean verify` thành công với 11 unit/security test và 13 PostgreSQL Testcontainers integration test. Flyway áp dụng thành công V001, V002, V003.
