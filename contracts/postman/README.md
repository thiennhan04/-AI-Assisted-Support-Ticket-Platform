# Hướng dẫn test API local bằng Postman

## Chuẩn bị

1. Chạy Docker container `postgres` và `redis`.
2. Chạy Identity Service ở port `8081` với profile `local` và bật seed tài khoản demo.
3. Chạy Ticket Service ở port `8082` với profile `local`.
4. Trong Postman, chọn Import và mở `local-api-tests.postman_collection.json` trong thư mục này.

Collection dùng tenant `acme`, tài khoản `customer@acme.local`, `agent@acme.local` và password local `ChangeMe123!`. Nếu đã dùng password khác khi tạo user, sửa biến `password` trong collection. Seed không phải cơ chế đổi password cho user đã tồn tại.

## Thứ tự chạy

Chạy từng request theo số thứ tự; có thể xem status, response body và tab Test Results sau mỗi lần Send.

| Request | Mục đích | Status mong đợi |
|---|---|---|
| 01–02 | Kiểm tra health của hai service | 200 |
| 03–04 | Login customer và agent, lưu token tự động | 200 |
| 05 | Tạo ticket, tự sinh UUID key và lưu ticket ID/ETag | 201 |
| 06 | Gửi lại cùng body/key, xác nhận cùng ticket ID | 201 |
| 07 | Dùng lại key với body khác | 409 |
| 08–09 | Xem chi tiết và tìm kiếm ticket | 200 |
| 10 | Customer sửa nội dung ticket đang OPEN | 200 |
| 11 | Cố sửa bằng version cũ `"0"` | 412 |
| 12 | Cố sửa khi thiếu `If-Match` | 428 |
| 13 | Customer thêm public comment | 201 |
| 14 | Customer cố thêm internal comment | 403 |
| 15 | Agent thêm internal comment | 201 |
| 16–17 | So sánh comment customer và agent nhìn thấy | 200 |
| 18–20 | Agent bắt đầu xử lý, giải quyết; customer đóng ticket | 200 |
| 21 | Rotate refresh token và lưu cặp token mới | 200 |
| 22 | Logout, thu hồi refresh-token family | 204 |
| 23 | Cố refresh sau logout | 401 |

Response 4xx ở các request kiểm tra lỗi là kết quả mong đợi. Script trong collection kiểm tra status và tự chuyển dữ liệu sang request sau.

Request 05 luôn sinh key mới trước khi gửi, vì vậy bấm Send lần nữa sẽ tạo ticket mới. Dùng request 06 để kiểm tra replay với key và nội dung giống lần tạo trước. Mỗi lần chạy lại toàn bộ kịch bản tạo một ticket mới; không có bước xóa dữ liệu.

Request 11 dùng ETag ban đầu `"0"`, chỉ chạy sau request 10 đã tăng version. Nếu sửa hoặc thêm comment bằng công cụ khác, chạy request 08 để lấy ETag mới trước thao tác tiếp theo. Token có hiệu lực 15 phút; nếu hết hạn, login lại tương ứng.

Logout thu hồi refresh-token family; access JWT đã phát vẫn có thể dùng đến khi hết hạn. Vì vậy request 23 kiểm tra refresh bị từ chối, không kiểm tra access JWT bị vô hiệu hóa ngay.

## Khi gọi thủ công

API ticket luôn cần header:

```http
Authorization: Bearer <accessToken>
```

Tạo ticket cần thêm:

```http
Idempotency-Key: <new-uuid>
Content-Type: application/json
```

```json
{
  "subject": "Cannot sign in",
  "description": "The customer cannot sign in after resetting the password",
  "priority": "HIGH"
}
```

Không gửi `tenantId`, `requesterId` hoặc `status` trong body tạo ticket. Backend lấy tenant/requester từ JWT và đặt trạng thái ban đầu là OPEN.

Sửa ticket hoặc thêm comment cần `If-Match` lấy nguyên giá trị ETag từ response xem chi tiết, kể cả dấu ngoặc kép:

```http
If-Match: "0"
```

Sau mỗi mutation thành công, collection tự lưu ETag mới để dùng tiếp.

## Lưu ý dữ liệu local

Collection chỉ dùng credential demo. Sau khi chạy, biến collection chứa access/refresh token; xóa giá trị token trước khi export hoặc chia sẻ. Không dùng collection này cho môi trường production.
