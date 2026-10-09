# DD-301 — Upload Knowledge bằng một API

## Contract hiện tại

Theo quyết định của người dùng ngày 2026-10-07, thay signed upload hai bước bằng một request:

```text
POST /v1/knowledge/documents
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
Content-Type: multipart/form-data
```

Hai phần bắt buộc: `metadata` (JSON, Content-Type application/json) và `file` (File).
Metadata chỉ có title, visibility, tags; không nhận tenantId, fileName, mimeType, sizeBytes hoặc sha256.
Tên file lấy từ multipart filename, loại đường dẫn/control character. Tenant lấy từ JWT.
MIME, size và SHA-256 lấy/tính từ byte thật. PDF/DOCX/TXT UTF-8 không rỗng, tối đa 25 MiB.

Response 201 chứa DocumentResponse với status QUEUED. Không còn upload URL, uploadFields, expiry hay complete.
Gửi lại request đã thành công tạo document mới; **chưa có upload idempotency**.

## Flow và cách đọc source

1. DocumentController.upload đọc multipart và validate metadata.
2. DocumentService.upload kiểm tra ADMIN, file không rỗng, size và tên file.
3. Sinh document/version ID và key opaque documents/{tenantId}/{versionId}.
4. MinioDocumentStorageAdapter.upload đọc có giới hạn, nhận diện định dạng, tính SHA-256 rồi lưu đúng byte đã kiểm tra.
5. Service lưu document/version QUEUED cùng một ingestion event trong transaction DB.
6. Trả metadata cho client.

Bắt đầu đọc DocumentUploadIT, rồi controller, CreateDocumentRequest, DocumentService.upload.
Chi tiết provider nằm trong adapter; vẫn JPA Repository, không thêm production JDBC.
Application nhận trực tiếp DTO API và MultipartFile theo lựa chọn đơn giản hóa; không có Command/toCommand.
Controller không chứa business logic.

## Giới hạn và lỗi

- Multipart servlet: file tối đa 25 MiB, toàn request tối đa 26 MiB; quá lớn trả 413.
- Adapter đọc tối đa 25 MiB + 1 byte và cho tối đa hai upload đồng thời để giới hạn bộ nhớ.
- Không tin Content-Type/đuôi file; server nhận diện nội dung. Binary không hỗ trợ trả 422.
- Metadata/missing part/file rỗng không hợp lệ trả 400; metadata part không phải JSON trả 415.
- ADMIN mới được quản trị; token không hợp lệ trả 401, thiếu quyền 403, document khác tenant 404.
- MinIO/persistence lỗi hoặc adapter bận trả 503, không lộ provider/SQL trong response.
- MinIO connect timeout 5 giây, read/write timeout 30 giây. Bucket private; không trả object key/download URL.
- MIME detection không thay parser đầy đủ: tài liệu hỏng/mã hóa/số trang ở DD-302; scanning ở DD-602.
- LIMITED_ACCESS chưa có API ACL/search; metadata không tự cấp quyền đọc nội dung.

DB và MinIO không có transaction chung. MinIO lỗi thì không ghi metadata/event.
DB/outbox lỗi thì DB rollback nhưng object đã lưu có thể thành orphan. Không tự xóa khi kết quả commit chưa chắc chắn;
cần chính sách dọn orphan theo tuổi. Retry tạo ID/key mới, không ghi đè document đã nhận.
Transaction bao quanh flow để giữ metadata và outbox nguyên tử; chưa tối ưu tách storage I/O khỏi transaction.

QUEUED là yêu cầu ingestion được lưu trong outbox, **không phải worker đang chạy**.
Extraction/chunk ở DD-302, embedding/search DD-303, replacement/delete DD-304; publisher/consumer nối ở DD-501.

## Migration từ bản cũ

Giữ nguyên V001, thêm V002__single_request_upload.sql. Reservation UPLOADING chuyển FAILED,
errorCode UPLOAD_FLOW_REPLACED; không xóa metadata. Version QUEUED/ACTIVE giữ nguyên.
Bỏ upload_expires_at và expiry index. Object staging cũ không tự xóa.
Không sửa migration đã áp dụng hoặc reset DB.

POST /documents/uploads và POST /documents/{id}/complete cũ đã bỏ.
Status UPLOADING vẫn được giữ trong enum cho lịch sử; request mới chỉ lưu document sau khi file được nhận, ở trạng thái QUEUED.

## Chạy và test

```powershell
docker compose --env-file .env -f deploy/compose.yaml up -d postgres minio minio-init
./mvnw.cmd -B -pl services/knowledge-service -am clean verify
```

Chạy Identity 8081 để login/JWKS, KnowledgeServiceApplication Java 21 profile local ở **8084**.
Không cần Ticket/Redis/RabbitMQ cho upload hiện tại.
DB mặc định knowledge_db, user knowledge_service, password local-knowledge-db; MinIO http://localhost:9000, bucket knowledge.

Override: DB_URL, DB_USERNAME, DB_PASSWORD, OBJECT_STORE_ENDPOINT, OBJECT_STORE_BUCKET,
OBJECT_STORE_ACCESS_KEY, OBJECT_STORE_SECRET_KEY, JWT_ISSUER, JWT_AUDIENCE, JWKS_URI.
MinIO endpoint chỉ cần truy cập từ JVM; Postman không kết nối MinIO nữa.
Spring không tự đọc .env Compose. Không dùng credential mặc định local ngoài máy dev.

Test dùng PostgreSQL/MinIO thật qua Testcontainers, không sửa DB local: một request, byte/checksum thực,
MIME giả, tenant/role, request invalid, missing parts, outbox rollback và file vượt 25 MiB qua HTTP/Tomcat thật.

Kết quả ngày 2026-10-07: clean verify Knowledge và shared libs pass, 16 unit test + 9 integration test, không skip.
Test nâng cấp database từ V001 sang phiên bản mới nhất xác nhận giữ nguyên document QUEUED/ACTIVE, giữ active version, chuyển reservation cũ sang FAILED và đổi đủ ba giá trị phạm vi truy cập.

Ngày 2026-10-08: enum DocumentAccessScope dùng ALL_TENANT_USERS, SUPPORT_TEAM_ONLY (mặc định), LIMITED_ACCESS.
V003__rename_document_access_scopes.sql chuyển dữ liệu và CHECK constraint từ tên cũ; không sửa V001/V002.
Field API và cột DB vẫn tên visibility. Client cần gửi tên mới; khởi động lại Knowledge để Flyway áp dụng V003.

Xem [hướng dẫn Postman](dd-301-postman.md).
