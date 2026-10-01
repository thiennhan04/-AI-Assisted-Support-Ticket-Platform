# Backlog triển khai và tiêu chí chấp nhận

## Epic 0 - Repository và hạ tầng

### DD-001 Khởi tạo monorepo

- Tạo năm Spring Boot module và các thư viện dùng chung được phép.
- Thêm Java 21 toolchain, format, test và container build.
- Chấp nhận: mọi module compile/test từ root; không có dependency domain giữa các service.

### DD-002 Hạ tầng local

- Dùng Compose cho PostgreSQL database/user, RabbitMQ, Redis, MinIO, OTel, Prometheus và Grafana.
- Chấp nhận: health check xanh; data volume bền vững; credential được ghi đè qua môi trường.

## Epic 1 - Identity

### DD-101 Login và JWT

- Triển khai tenant/user/role, password hashing, login và ký RS256.
- Chấp nhận: token hợp lệ chứa các claim bắt buộc; credential sai dùng response 401 chung.

### DD-102 Refresh rotation

- Triển khai rotating token family, logout và phát hiện reuse.
- Chấp nhận: hai refresh call đồng thời có một lần thành công; reuse thu hồi cả family.

### DD-103 Tích hợp resource server

- Cấu hình các service khác xác minh JWKS và ánh xạ principal.
- Chấp nhận: từ chối issuer/audience/tenant claim sai; fixture key rotation thành công.

## Epic 2 - Ticket core

### DD-201 Ticket CRUD/query

- Triển khai aggregate, state machine, list filter và phân quyền.
- Chấp nhận: role/tenant matrix và mọi transition được kiểm thử.

### DD-202 Optimistic locking/idempotency

- Thêm xử lý ETag/If-Match và Idempotency-Key.
- Chấp nhận: update dùng version cũ trả 412; replay create giống hệt trả resource ban đầu.

### DD-203 Comment và audit

- Thêm public/internal comment và append-only audit.
- Chấp nhận: customer không thấy internal comment; mọi mutation đều tạo audit.

### DD-204 Transactional outbox

- Lưu/phát ticket event dùng broker confirm.
- Chấp nhận: mô phỏng publish lỗi vẫn để row có thể retry; không mất event sau restart.

## Epic 3 - Knowledge

### DD-301 Luồng upload

- Triển khai document metadata, signed upload và xác minh hoàn tất.
- Chấp nhận: bắt buộc MIME/size/checksum; upload chưa hoàn tất sẽ hết hạn.

### DD-302 Trích xuất và chia chunk

- Triển khai adapter PDF/DOCX/TXT và deterministic chunker.
- Chấp nhận: fixture giữ metadata trang/heading; tài liệu rỗng/mã hóa thất bại an toàn.

### DD-303 Embedding và vector search

- Triển khai fake/real embedding port và truy vấn pgvector.
- Chấp nhận: bộ lọc active-version/tenant/ACL nằm trong SQL; chạy được đánh giá Recall@5.

### DD-304 Thay thế/xóa phiên bản

- Stage phiên bản thay thế và chuyển active nguyên tử.
- Chấp nhận: tìm kiếm không gián đoạn; thao tác xóa loại document khỏi kết quả ngay lập tức.

## Epic 4 - AI Orchestration

### DD-401 Vòng đời AI job

- Triển khai job idempotency, input hash và state machine.
- Chấp nhận: request giống hệt dùng lại job; input xung đột trả 409.

### DD-402 Phân tích ticket

- Triển khai prompt version, structured output và validation.
- Chấp nhận: luôn kiểm tra output schema; chỉ repair một lần; ghi nhận metric.

### DD-403 Bản nháp dựa trên tri thức

- Tích hợp Knowledge search và kiểm tra citation.
- Chấp nhận: từ chối citation bịa đặt; không có tri thức tạo bản nháp có cờ cảnh báo/từ chối phù hợp.

### DD-404 Khả năng phục hồi/chi phí provider

- Triển khai timeout, retry, circuit breaker, token/cost record và budget.
- Chấp nhận: provider outage không ảnh hưởng ticket API; tenant hết budget không gọi provider.

## Epic 5 - Worker và projection

### DD-501 Event consumer/retry/DLQ

- Triển khai queue/pool riêng và lịch retry.
- Chấp nhận: delivery trùng chỉ tạo một job; poison message tới DLQ với lỗi an toàn.

### DD-502 Ticket AI projection

- Consume kết quả analysis/draft theo cách idempotent.
- Chấp nhận: kết quả cũ bị đánh dấu stale; field ticket đã chấp nhận không bị ghi đè.

### DD-503 Phê duyệt draft/feedback

- Thêm thao tác agent phê duyệt dưới dạng comment thường và feedback event.
- Chấp nhận: draft gốc bất biến; nội dung đã chỉnh sửa và gửi được audit.

## Epic 6 - Chất lượng và vận hành

### DD-601 Khả năng quan sát

- Thêm metric/tracing/dashboard và alert.
- Chấp nhận: một trace đi xuyên suốt ticket event, Worker, Orchestrator, Knowledge và provider adapter.

### DD-602 Tăng cường bảo mật

- Thêm rate limit, hook quét upload, PII redaction và fixture prompt injection.
- Chấp nhận: các test trong security design thành công.

### DD-603 Đánh giá và hiệu năng

- Thêm dataset phân loại/RAG và kịch bản tải.
- Chấp nhận: báo cáo metric thực tế; đạt release gate hoặc ghi rõ ngoại lệ.

### DD-604 Demo và tài liệu

- Thêm sơ đồ kiến trúc, API example, demo seed và kịch bản hai phút.
- Chấp nhận: developer mới chạy được end-to-end flow với fake provider trên máy sạch theo lệnh đã ghi.

## Gợi ý chia theo tám tuần

| Tuần | Nội dung bàn giao |
|---|---|
| 1 | DD-001..002, DD-101, cấu hình resource server cơ bản |
| 2 | DD-201..203 |
| 3 | DD-204, DD-301..302 |
| 4 | DD-303..304 với fake embedding |
| 5 | DD-401..402 với fake model |
| 6 | DD-403..404 và Worker consumer |
| 7 | Projection, approval, security và observability |
| 8 | Evaluation, load test, demo và bằng chứng cho CV |
