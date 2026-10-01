# Ghi chú triển khai DD-201

## Phạm vi đã bàn giao

- Thêm Ticket aggregate với validation, content versioning, assignment behavior và đầy đủ lifecycle state machine.
- Thêm phân quyền dựa trên principal cho CUSTOMER, AGENT, ADMIN và cô lập tenant theo nguyên tắc fail closed.
- Thêm `POST /v1/tickets`, `GET /v1/tickets/{ticketId}`, `GET /v1/tickets`, `PATCH /v1/tickets/{ticketId}`.
- Thêm lọc theo status, priority, category, assignee, requester, thời gian tạo và text; pagination có giới hạn và sorting theo allow-list.
- Thêm JPA persistence và Flyway `V001` với constraint, sequence, tenant index và PostgreSQL full-text GIN index.
- Thêm Problem Details response với ticket error code ổn định và correlation ID.
- Refactor aggregate factory, authorization policy, command orchestration và từ vựng integration test để ý nghĩa lifecycle rõ ràng mà không phải giải mã raw JSON hoặc UUID vô danh.
- Thêm `dd-201-reading-guide.md` làm lộ trình review bắt đầu từ production code cho developer mới.

## Kiểm tra

- Domain test bao phủ mọi transition được phép/bị từ chối, tính bất biến của ticket đã đóng, tăng content version và assignment behavior.
- Tám integration test dùng PostgreSQL Testcontainers kiểm tra riêng authentication, từ chối tenant từ request, giá trị mặc định khi tạo, quyền xem theo role/tenant, management policy, lifecycle transition, transition sai, filter và pagination.
- `mvn verify` không cần PostgreSQL do developer tự chạy; integration database dùng một lần và do Testcontainers quản lý.
- Toàn monorepo `./mvnw.cmd -B clean verify` thành công với 48 test. Ticket Service đóng góp 10 unit/security test và 8 PostgreSQL integration test.
- Image DD-201 ban đầu là `ticket-platform/ticket-service:0.1.0-SNAPSHOT` (`46887b60424f`). Cần build lại sau readability refactor để image và source revision truy vết được cùng nhau.

## Chủ động trì hoãn

- DD-202: `Idempotency-Key`, ETag/`If-Match` và optimistic-concurrency response contract phía ngoài.
- DD-203: comment và audit trail.
- DD-204: transactional outbox và ticket event.
