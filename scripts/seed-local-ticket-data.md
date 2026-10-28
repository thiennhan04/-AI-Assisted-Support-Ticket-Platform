# Dữ liệu ticket mẫu ở local

Script `seed-local-ticket-data.sql` thêm năm ticket snapshot cho tenant demo `acme`, mỗi ticket có một trạng thái: OPEN, IN_PROGRESS, WAITING_CUSTOMER, RESOLVED, CLOSED. Tất cả thuộc customer demo; ticket đã xử lý được gán cho agent demo. Script thêm ba comment (hai public, một internal), cùng audit tạo ticket/thêm comment.

## Điều kiện

- PostgreSQL local của project đang chạy và Ticket Service đã áp dụng migration.
- Tenant `acme`, customer `customer@acme.local` và agent `agent@acme.local` đã được seed với các UUID mặc định của Identity.
- Chỉ chạy trong `ticket_db` của môi trường local.

Từ root repository, chạy PowerShell:

```powershell
Get-Content -Raw scripts/seed-local-ticket-data.sql | docker compose --env-file .env -f deploy/compose.yaml exec -T postgres psql -v ON_ERROR_STOP=1 -U ticket_service -d ticket_db
```

Script dùng một transaction và UUID cố định. Chạy lại không tạo trùng ticket/comment hoặc ghi đè dữ liệu hiện có. Ticket number được lấy từ sequence của database.

Đây là dữ liệu snapshot ghi trực tiếp vào DB để thử đọc/lọc/phân quyền. Audit chỉ ghi thao tác tạo fixture và thêm comment, không mô phỏng toàn bộ lịch sử chuyển trạng thái. Script không tạo outbox event; để test DD-204, tạo ticket qua API.

## Xem dữ liệu

Trong DBeaver, kết nối `ticket_db`, mở schema `ticket` và refresh bảng. Chạy:

```sql
SELECT id, ticket_number, subject, status, priority, version
FROM ticket.ticket
WHERE subject LIKE 'Demo:%'
ORDER BY ticket_number;
```

Đăng nhập bằng customer demo để thấy cả năm ticket qua `GET /v1/tickets`. Agent demo có thể thấy thêm internal comment mà customer không thấy. Lấy ETag hiện tại từ API chi tiết trước khi sửa hoặc thêm comment.
