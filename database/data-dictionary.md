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

`knowledge_document` là danh tính ổn định. `document_version` biểu diễn mỗi lần ingest/version.
`document_chunk` thuộc đúng một immutable version. Chỉ chunk thuộc `active_version_id` của document
được search.

Vector dimension bị ràng buộc bởi schema. Không âm thầm chèn embedding từ model có dimension khác.

## 6. Chiến lược migration

- Mỗi service sở hữu `V001__baseline.sql`, các forward migration sau đó và Flyway history.
- Không bao giờ sửa migration đã được áp dụng.
- Thay đổi phá hủy dùng chiến lược expand-migrate-contract qua nhiều release.
- Index lớn dùng thao tác online/concurrent khi database hỗ trợ.
- Chỉ seed reference value; user test/demo thuộc script của local profile.
