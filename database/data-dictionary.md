# Từ điển dữ liệu và quyền sở hữu

## 1. Quy tắc chung

- UUID được sinh phía application bằng UUIDv7 khi được hỗ trợ; UUIDv4 vẫn được chấp nhận.
- Mọi timestamp dùng `timestamptz` UTC.
- Bảng entity có thể sửa bởi người dùng phải có optimistic-lock `version`.
- Bảng thuộc tenant bắt buộc có `tenant_id` dù có thể suy ra, để hỗ trợ indexed predicate an toàn.
- Foreign key không đi xuyên database của service. ID từ service khác chỉ là tham chiếu được xác minh
  bằng claim/API/event.
- JSONB chỉ dùng cho immutable snapshot hoặc metadata linh hoạt, không dùng cho core state cần search.

## 2. Identity database

| Bảng | Mục đích | Field nhạy cảm | Xóa dữ liệu |
|---|---|---|---|
| `tenant` | Vòng đời tenant | không | suspend trước khi xóa |
| `app_user` | Danh tính đăng nhập | email, password hash | ẩn danh hoặc lưu theo policy |
| `user_role` | Role của user trong tenant | không | cascade cùng user |
| `refresh_session` | Rotation/revocation | token hash, IP/agent nếu bổ sung | dọn sau thời hạn + 30 ngày |

## 3. Ticket database

| Bảng | Mục đích | Kiểu cập nhật |
|---|---|---|
| `ticket` | Aggregate root của ticket | optimistic locking |
| `ticket_comment` | Hội thoại append-only | không sửa; đính chính bằng entry mới |
| `ticket_attachment` | Object metadata/status | lifecycle transition |
| `ticket_ai_analysis` | Bản chiếu kết quả AI | upsert một lần theo job |
| `ticket_ai_draft` | Bản chiếu draft/phê duyệt | kết quả bất biến, chỉ sửa approval metadata |
| `ticket_audit` | Audit trail append-only | chỉ insert |
| `idempotency_record` | Chống command replay | dọn theo TTL |
| `outbox_event` | Phát event tin cậy | đánh dấu published |
| `processed_event` | Consumer deduplication | TTL >= cửa sổ replay của nguồn |

## 4. AI database

`ai_job.input_payload` có thể chứa nội dung ticket và là dữ liệu nhạy cảm. Áp dụng retention 30 ngày
hoặc che dữ liệu sau khi kết thúc, nhưng giữ hash/metric. `prompt_template` là dữ liệu audit cấu hình,
không được hard-delete sau khi dùng. `model_usage` không chứa raw prompt/output.

## 5. Knowledge database

DD-301 dùng Flyway V001, V002__single_request_upload.sql và V003__rename_document_access_scopes.sql cho `knowledge_document`, `document_version`, `outbox_event`.
Document giữ `current_version_id`; `active_version_id` vẫn NULL đến khi ingestion kích hoạt.
`file_name` lấy từ multipart filename đã loại đường dẫn; MIME/size/SHA-256 được xác minh/tính từ byte thật.
V002 bỏ upload_expires_at và chuyển reservation UPLOADING cũ sang FAILED/UPLOAD_FLOW_REPLACED, không xóa metadata.
V003 đổi giá trị visibility sang ALL_TENANT_USERS, SUPPORT_TEAM_ONLY, LIMITED_ACCESS và chuyển dữ liệu cũ tương ứng; giữ nguyên tên cột/field visibility.
Upload mới lưu trực tiếp key `documents/` và trạng thái QUEUED. SHA-256 chỉ có thể NULL ở dữ liệu lịch sử chưa được nhận.

`knowledge_document` là danh tính ổn định. `document_version` biểu diễn mỗi lần ingest/version.
`document_chunk` thuộc đúng một immutable version. Chỉ chunk thuộc `active_version_id` của document
được search.

Vector dimension bị ràng buộc bởi schema. Không âm thầm chèn embedding từ model có dimension khác.

DD-302 thêm Flyway V004__document_chunks.sql: document_version có chunking_version và prepared_at;
document_chunk lưu ordinal từ 0, page_number nullable, heading, content, content_checksum SHA-256,
token_count và created_at. UNIQUE(version, ordinal) chống chunk trùng; hai composite FK bảo đảm chunk
thuộc đúng document/version/tenant. Chưa thêm embedding hoặc search_vector vào migration DD-302.
prepared_at chỉ được ghi cùng transaction lưu đủ chunk. Trạng thái sau chuẩn bị là PROCESSING,
active_version_id không đổi; chưa được search trước bước embedding/kích hoạt của DD-303/DD-304.

DD-303 thêm V005__embedding_search.sql: document_chunk.embedding là vector(1536), NULL trong staging.
document_version lưu embedding_model, embedding_dimension=1536 và activated_at.
Chỉ activate khi toàn bộ chunk có vector hợp lệ; active_version_id và các vector commit cùng transaction.
document_acl cấp quyền USER/ROLE cho LIMITED_ACCESS; ADMIN vẫn cần ACL khi tìm restricted document.
Search dùng exact cosine sau khi lọc tenant/active/model/ACL/tags, chưa thêm ANN/full-text index.

## 6. Chiến lược migration

- Mỗi service sở hữu `V001__baseline.sql`, các forward migration sau đó và Flyway history.
- Không bao giờ sửa migration đã được áp dụng.
- Thay đổi phá hủy dùng chiến lược expand-migrate-contract qua nhiều release.
- Index lớn dùng thao tác online/concurrent khi database hỗ trợ.
- Chỉ seed reference value; user test/demo thuộc script của local profile.
