# Test Knowledge upload bằng một request Postman

Chạy Identity 8081, Knowledge 8084, PostgreSQL và MinIO theo [ghi chú triển khai](dd-301-knowledge-upload.md).

## 1. Login ADMIN

```text
POST http://localhost:8081/v1/auth/login
Content-Type: application/json
```

```json
{"tenantCode":"acme","email":"admin@acme.local","password":"<LOCAL_SEED_PASSWORD>"}
```

Thay password bằng mật khẩu đã seed (ví dụ ChangeMe123! nếu trước đó dùng mật khẩu đó). Lấy accessToken.
Đổi biến seed không đổi password của user đã tồn tại.

## 2. Upload một lần

```text
POST http://localhost:8084/v1/knowledge/documents
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
```

Body → **form-data**, tạo hai dòng:

| Key | Loại | Giá trị | Content Type của part |
|---|---|---|---|
| metadata | Text | JSON bên dưới | application/json |
| file | File | Chọn PDF, DOCX hoặc TXT UTF-8 | Postman tự đặt |

```json
{"title":"Account reset guide","visibility":"SUPPORT_TEAM_ONLY","tags":["account"]}
```

Bật cột **Content Type** trong bảng form-data nếu đang ẩn rồi đặt dòng metadata là application/json.
Không tự thêm header HTTP Content-Type: Postman phải tạo multipart/form-data cùng boundary.
Content-Type của riêng part metadata khác với header của toàn request.
Không chọn raw JSON/binary cho toàn Body.

Không gửi tenantId, fileName, mimeType, sizeBytes hoặc sha256 trong JSON.
Server tự lấy/tính từ file. Không gọi MinIO hay complete, không cần tính checksum trước.

Kỳ vọng **201 Created**, có documentId, versionId, status QUEUED, mimeType, sizeBytes, sha256.

## 3. Xem metadata

```text
GET http://localhost:8084/v1/knowledge/documents/<documentId>
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
```

```text
GET http://localhost:8084/v1/knowledge/documents?page=0&size=20
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
```

List trả array, mới nhất trước. Chưa có consumer nên status chưa tự lên ACTIVE.
DBeaver: database knowledge_db, schema knowledge, bảng knowledge_document, document_version, outbox_event.

## 4. Test lỗi

- Thiếu file/metadata hoặc file rỗng → 400.
- Metadata thiếu title, title rỗng hoặc thêm tenantId → 400.
- Metadata part không đặt application/json → 415.
- File lớn hơn 25 MiB hoặc toàn multipart lớn hơn 26 MiB → 413.
- Binary không thuộc định dạng hỗ trợ, dù tên/Content-Type khai báo PDF → 422 UNSUPPORTED_DOCUMENT_TYPE.
- Token CUSTOMER/AGENT → 403; token thiếu/sai/hết hạn → 401.
- ADMIN tenant khác đọc document → 404.
- MinIO lỗi → 503; không tạo metadata/outbox.
- Gửi lại request đã thành công → tạo document mới; chưa có upload idempotency.

API /documents/uploads và /documents/{id}/complete cũ đã bỏ.
