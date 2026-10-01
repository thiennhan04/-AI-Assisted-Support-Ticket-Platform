# Quyết định contract DD-201

## Tenant và danh tính caller

- `tenantId` và `requesterId` không bao giờ lấy từ request input. Chúng được lấy từ `AuthenticatedPrincipal` đã xác minh do DD-103 tạo.
- Mọi repository lookup đều có `tenant_id`. Ticket thuộc tenant khác được trả thành `404 TICKET_NOT_FOUND` để không tiết lộ sự tồn tại.
- Quyền xem danh sách/chi tiết của CUSTOMER còn bị giới hạn ở ticket mà caller là requester. AGENT và ADMIN xem được mọi ticket trong tenant của mình.
- JSON property không biết bị từ chối, nên client gửi `tenantId` tự kiểm soát sẽ nhận 400.

## Command và phân quyền

- Bất kỳ platform role đã xác thực nào cũng có thể tạo ticket; caller trở thành requester.
- CUSTOMER chỉ có thể sửa subject/description trên ticket OPEN của chính mình.
- AGENT và ADMIN có thể thay đổi content, priority, category và assignment trong tenant.
- CUSTOMER chỉ được thực hiện transition phía requester: `WAITING_CUSTOMER` sang `IN_PROGRESS`, và `RESOLVED` sang `IN_PROGRESS` hoặc `CLOSED`.
- AGENT/ADMIN thực hiện transition phía hỗ trợ. Chỉ ADMIN được mở lại ticket `CLOSED`.
- Assignee UUID hiện là tham chiếu opaque đã được phân quyền. Việc xác minh đó là agent active cùng tenant được hoãn đến khi có contract user-directory từ Identity.

## State và persistence

- Ticket number dùng database sequence `ticket.ticket_number_seq` và format `SUP-%08d`; UUID vẫn là định danh chuẩn.
- `contentVersion` chỉ tăng khi subject hoặc description thay đổi. JPA `version` bảo vệ aggregate đã lưu và được trả cho client.
- Flyway migration sở hữu schema Ticket, constraint, index và full-text GIN index.
- DD-201 triển khai `q` bằng phép tìm subject/description không phân biệt hoa thường. Có thể tối ưu query plan bằng PostgreSQL full-text operator mà không đổi HTTP contract.

## Ranh giới epic có chủ đích

- OpenAPI đã mô tả `Idempotency-Key` và `If-Match`/ETag là API mục tiêu. Việc bắt buộc các cơ chế này thuộc DD-202, nên DD-201 không giả vờ đã có các bảo đảm đó.
- Comment/audit thuộc DD-203. Transactional outbox thuộc DD-204.
