# Hướng dẫn đọc và review DD-201

Đây là đường đọc ngắn nhất để hiểu Ticket CRUD/query. Đọc production flow trước và integration test sau cùng.

## Thứ tự đọc

1. `domain/TicketStatus` liệt kê các trạng thái lifecycle.
2. `domain/TicketTest` minh họa các quy tắc aggregate bằng ví dụ nhỏ.
3. `domain/Ticket` sở hữu state transition và mutation invariant.
4. `application/TicketPolicy` trả lời ai được xem, sửa, quản lý hoặc chuyển trạng thái ticket.
5. `application/TicketCommandService` điều phối write use case và transaction boundary.
6. `application/TicketQueryService` tạo query criteria an toàn theo tenant.
7. `api/TicketController` ánh xạ HTTP input sang application command/query.
8. `infrastructure/persistence/JpaTicketRepositoryAdapter` triển khai repository port.
9. `TicketCrudIT` xác minh mọi tầng hoạt động cùng PostgreSQL.

## Luồng tạo

```text
POST /v1/tickets
  -> CreateTicketRequest
  -> TicketCommandService.create
  -> Ticket.open (new tickets always start OPEN)
  -> TicketRepository.create
  -> PostgreSQL
```

`tenantId` và `requesterId` đến từ `AuthenticatedPrincipal`, không phải request body. Nếu thiếu priority, application service dùng `MEDIUM`.

## Luồng cập nhật

```text
PATCH /v1/tickets/{id}
  -> UpdateTicketRequest
  -> TicketCommandService.update
       1. load by tenant and id
       2. apply content changes
       3. apply support-managed fields
       4. apply status change
  -> TicketRepository.save
```

Mỗi nhóm trách nhiệm có một nơi sở hữu:

| Mối quan tâm | Nơi sở hữu |
|---|---|
| Cấu trúc request và giới hạn kích thước | API DTO |
| Phân quyền caller và role | `TicketPolicy` |
| State transition được phép | `Ticket` |
| Transaction và thứ tự thao tác | `TicketCommandService` |
| Đọc/ghi giới hạn theo tenant | Repository port và adapter |

Assignment được áp dụng trước status change rõ ràng vì assign một ticket OPEN sẽ bắt đầu xử lý và chuyển nó sang `IN_PROGRESS`.

## Luồng query

`TicketQueryService` luôn đưa tenant của principal vào `TicketSearchCriteria`. Với CUSTOMER, service còn đưa caller vào `visibleRequesterId`; requester filter do client truyền chỉ có thể thu hẹp kết quả, không thể mở rộng quyền xem.

## Mục đích kiểm thử

- `TicketTest` là đặc tả state machine có thể thực thi.
- `ResourceServerSecurityTest` kiểm tra niềm tin JWT/JWKS và chuyển đổi principal.
- `TicketCrudIT` dùng actor có tên như `ACME_CUSTOMER` và helper mang nghĩa như `changeStatusAs`; mỗi test mô tả một quy tắc nhìn thấy từ bên ngoài.

DD-202 bổ sung idempotent create và ETag/`If-Match`. Không trộn các mối quan tâm đó vào domain rule của DD-201.
