# DD-304 – Thay phiên bản và xóa tài liệu Knowledge

## Kết quả và luồng đọc source

Admin có thể thay file của cùng documentId và yêu cầu xóa tài liệu.
Tên tài liệu, visibility, tags và ACL giữ nguyên khi thay file. Version mới có UUID riêng và
versionNumber tăng 1. Không nhận metadata mới trong request thay file để tránh thay quyền của bản cũ đang active.

Đọc theo thứ tự:

1. DocumentLifecycleIT: xem ví dụ thay thành công, thất bại, xóa và khôi phục cleanup sau crash.
2. DocumentController.replace/delete và DocumentService.replace/delete: nhận request, kiểm tra ADMIN/If-Match/file.
3. DocumentLifecycleRepository và JpaDocumentLifecycleRepositoryAdapter.stageReplacement/requestDeletion.
4. JpaKnowledgeIndexRepositoryAdapter.activate: chuyển active, retire bản cũ và ghi outbox nguyên tử.
5. DocumentCleanupService.processNext: claim → xóa MinIO → hoàn tất trong DB.
6. claimCleanup/completeCleanup/retryCleanup, DocumentVersionSpringDataRepository và migration V006.
7. DocumentCleanupJob và DocumentCleanupProperties: lịch chạy và cấu hình.

## Thay file theo phiên bản

```text
V1 ACTIVE, current=V1, active=V1
→ POST file V2
→ V2 QUEUED, current=V2, active=V1
→ prepare V2: PROCESSING, active vẫn V1
→ index V2 thành công: V2 ACTIVE, active=V2, V1 SUPERSEDED
→ Sau thời gian giữ bản cũ: xóa file/chunk V1, giữ metadata DELETED
```

Lấy tenant từ principal. Service kiểm tra điều kiện trước khi tải file lên MinIO; repository khóa
document và kiểm tra lại If-Match trước khi tạo version. Hai request cùng version chỉ một request thành công.
Một document chỉ có một version QUEUED/PROCESSING tại một thời điểm. Version FAILED được thay bằng version mới.
Không dùng lại UUID hoặc số version cũ. Nếu V2 lỗi, active V1 vẫn phục vụ tìm kiếm; có thể upload V3 thay V2.

File được xác minh/lưu ngoài transaction tạo replacement. Tạo version, đổi current_version_id và ghi
knowledge.ingest.requested.v1 nằm cùng transaction. Lỗi ghi outbox rollback metadata và con trỏ.
MinIO/DB không có distributed transaction: request thua cạnh tranh hoặc lỗi DB có thể để lại private object
chưa gắn metadata. Không xóa mù object khi kết quả commit chưa chắc chắn. Dọn orphan theo tuổi/đối chiếu DB
là hạng mục riêng, chưa triển khai trong DD-304; cleanup hiện tại chỉ xử lý version đã được ghi nhận.

Workflow prepare/index giữ nguyên entry point DD-302/DD-303. DD-501 nối publisher/consumer sau;
upload replacement chưa tự chạy ingestion khi chưa có workflow này.

## Kích hoạt nguyên tử

Trong một transaction có khóa document:

- Kiểm tra version hiện hành, trạng thái, đầy đủ vector hợp lệ.
- Lưu vector và thông tin model cho bản mới.
- Chuyển active_version_id sang bản mới; đánh dấu bản active cũ SUPERSEDED.
- Hẹn cleanup bản cũ sau grace-period, mặc định 24 giờ.
- Ghi knowledge.document.activated.v1 vào outbox, có previousVersionId (NULL cho lần đầu).

Nếu một bước lỗi, tất cả rollback, bao gồm retirement bản cũ và activation event.
Retry cùng version đã ACTIVE không sinh event trùng. Unique index giới hạn một activation event/version.
Consumer activation/audit và publisher outbox được nối ở DD-501.
Query DD-303 tự lấy active_version_id, không cần thay câu SQL để tìm bản mới.
Không có thao tác chuyển active trở lại version đã retire; muốn dùng lại nội dung cần tạo version mới.

## Xóa tài liệu

DELETE khóa document, xóa active_version_id và đặt document DELETING cùng transaction.
Mọi version chưa dọn được đánh dấu DELETING, cleanup_after=now; ACL được loại bỏ.
Sau khi transaction commit, truy vấn mới không trả tài liệu dù MinIO đang lỗi.
Truy vấn đã bắt đầu trước commit có thể hoàn tất theo snapshot cũ của PostgreSQL.
HTTP trả 202, chưa khẳng định file vật lý đã bị xóa. DELETE lặp lại là idempotent, vẫn trả 202.
Không cho thay file hoặc chạy ingestion/index trở lại trên document DELETING/DELETED.

Cleanup xóa object MinIO rồi xóa chunk/vector trong DB. Sau khi mọi version được dọn,
document chuyển DELETED. Giữ tombstone metadata và outbox để theo dõi; GET metadata ADMIN vẫn trả 200,
không cho restore document đã xóa. Không hard-delete row document/version nên các composite FK được giữ.
File đã dọn không có chức năng khôi phục trong ứng dụng; bản superseded chỉ còn file trong grace-period.
Với bucket bật object versioning, cần cấu hình thêm lifecycle cho noncurrent object versions ở MinIO.

## Cleanup có lease và retry

DocumentCleanupJob chạy mặc định mỗi 30 giây, tối đa 10 version mỗi lượt.
Không cần RabbitMQ để cleanup chạy. Có thể tắt job và gọi bean DocumentCleanupService trong test.

1. claimCleanup chọn document có việc đến hạn bằng FOR UPDATE SKIP LOCKED.
2. Trong transaction ngắn: kiểm tra version không còn current/active (trừ document DELETING),
   ghi cleanup_token và cleanup_lease_until, tăng cleanup_attempts.
3. Đóng transaction, gọi MinIO removeObject; object không còn tồn tại vẫn xem là xóa thành công.
4. completeCleanup khóa document, kiểm tra token còn đúng, xóa chunk và đánh dấu version DELETED.

Tất cả đường ghi khóa document trước rồi mới thay version để giữ thứ tự lock nhất quán.
MinIO I/O ngoài transaction DB. Service chết sau khi xóa file nhưng trước commit DB:
lease hết hạn, worker mới nhận việc và xóa lại idempotent. Token fencing ngăn worker cũ hoàn tất lease mới.
Đọc version qua Hibernate.unproxy khi cần vì query có thể trả lại proxy đã tồn tại trong persistence context.

Lỗi storage lưu cleanup_error_code=STORAGE_UNAVAILABLE, thử lại sau 1 phút; không đánh dấu DELETED sớm.
Lỗi DB để lease hết hạn rồi retry. Chưa giới hạn tổng số lần retry; cần giám sát job tồn đọng khi vận hành.
Cleanup tuyệt đối không nhận việc cho current/active version của document chưa xóa.

```yaml
knowledge:
  cleanup:
    enabled: true
    grace-period: 24h
    lease-duration: 2m
    retry-delay: 1m
    batch-size: 10
    poll-delay: 30s
```

Environment: DOCUMENT_CLEANUP_ENABLED và DOCUMENT_VERSION_GRACE_PERIOD. Các thuộc tính còn lại có thể
override bằng Spring configuration. Grace-period chỉ áp dụng bản bị thay thế; xóa document không chờ grace.
Không rút ngắn lease dưới một phút. Job chỉ xử lý objectKey lưu trong version, không quét/xóa bucket tùy ý.

## API để copy vào Postman

### Xem phiên bản hiện tại

```text
GET http://localhost:8084/v1/knowledge/documents/<documentId>
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
```

Response có ETag, ví dụ "1"; đây là token của phiên bản file hiện hành, dùng chống thay file dựa trên
version cũ. Nó không tăng theo từng thay đổi trạng thái và không dùng làm cache validator cho toàn bộ metadata.
status mô tả current version; documentStatus mô tả vòng đời document; activeVersionId chỉ phiên bản đang search.

### Thay file

```text
POST http://localhost:8084/v1/knowledge/documents/<documentId>/versions
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
If-Match: "1"
```

Body → form-data: một dòng file, chọn kiểu File và chọn file PDF/DOCX/TXT mới.
Không gửi metadata; để Postman tự tạo Content-Type multipart và boundary.
Thành công trả 201, versionNumber=2, ETag="2", activeVersionId vẫn là V1 cho đến activation.
If-Match thiếu → 428; sai cú pháp → 400; version cũ → 412; document đang xử lý/đã xóa → 409.
ADMIN khác tenant → 404; CUSTOMER/AGENT → 403. Giới hạn file vẫn 25 MiB.

### Xóa

```text
DELETE http://localhost:8084/v1/knowledge/documents/<documentId>
Authorization: Bearer <ADMIN_ACCESS_TOKEN>
```

Không cần body. Trả 202; GET metadata thấy DELETING, sau cleanup thấy DELETED.
Xem bảng knowledge.document_version: cleanup_after/cleanup_attempts/cleanup_error_code/cleaned_at
để theo dõi việc dọn, không tìm nội dung file trực tiếp trong bảng metadata.

## Migration và kiểm thử

V006 thêm SUPERSEDED, file_name theo version và các cột cleanup; backfill tên file lịch sử từ tên
đang lưu ở document vì schema cũ chưa lưu tên theo version. Không sửa V001–V005.
Thêm schema knowledge.document.activated.v1 và Java payload trong event-contracts.

```powershell
.\mvnw.cmd -B -pl services/knowledge-service -am clean verify
```

DocumentLifecycleIT chạy PostgreSQL/MinIO thật: giữ kết quả search V1 trong lúc V2 xử lý,
chuyển V2, thất bại và thay V3, hai request đồng thời, rollback khi outbox lỗi, xóa trong lúc index,
retry storage, recovery lease hết hạn, stale worker và bảo vệ current/active version.
DocumentCleanupServiceTest kiểm tra thứ tự storage/DB và lỗi DB sau khi đã xóa object.
Kết quả ngày 2026-10-24: clean verify Knowledge/shared libs thành công, 60 unit/security test và
29 integration test, không skip. Riêng DocumentLifecycleIT có 10 tình huống; migration V001→V006 cũng pass.
