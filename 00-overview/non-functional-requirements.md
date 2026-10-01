# Yêu cầu phi chức năng

## 1. Tính sẵn sàng và suy giảm có kiểm soát

- Core ticket read/write API: mục tiêu 99,9% mỗi tháng.
- Identity token endpoint: mục tiêu 99,9% mỗi tháng.
- Tính năng AI: mục tiêu 99,0%; lỗi AI không được làm giảm tính sẵn sàng của core API.
- Readiness chỉ unhealthy khi service không thực hiện được trách nhiệm chính. Lỗi model provider không
  làm Ticket Service unhealthy.

## 2. Mục tiêu hiệu năng

| Thao tác | p95 mục tiêu | Ghi chú |
|---|---:|---|
| Login | 500 ms | không tính external identity provider |
| Tạo/cập nhật ticket | 600 ms | không tính AI |
| Danh sách ticket | 800 ms | page size <= 50 |
| Knowledge vector search | 800 ms | topK <= 20 |
| AI analysis | 15 s | bất đồng bộ |
| Grounded draft | 25 s | bất đồng bộ |

## 3. Quy mô cơ sở

- 100 tenant.
- 10.000 active user.
- Tổng cộng 1.000.000 ticket.
- Burst 100 lần ghi ticket/giây.
- Duy trì 20 AI job/giây.
- 1.000.000 knowledge chunk.
- Nội dung ticket tối đa 20.000 ký tự Unicode.
- Tài liệu tối đa 25 MB và 500 trang.

## 4. Bảo mật

- TLS 1.2+ cho mọi đường truyền mạng.
- Secret chỉ lấy từ secret manager hoặc environment injection.
- Mật khẩu dùng Argon2id hoặc BCrypt với cost tối thiểu 12.
- Mọi query phải áp dụng tenant scope ở server.
- Object key phải opaque, không sinh trực tiếp từ tên file người dùng.
- AI input phải được phân loại và che dữ liệu theo policy trước khi gọi provider.
- Audit record chỉ được append ở tầng ứng dụng.

## 5. Thời gian lưu dữ liệu

| Dữ liệu | Mặc định |
|---|---|
| Ticket/audit | 3 năm hoặc theo policy của tenant |
| Refresh token | thời hạn token + 30 ngày metadata |
| AI raw input/output | 30 ngày |
| AI usage/quality metric | 13 tháng |
| Object của tài liệu đã xóa | xóa trong 7 ngày |
| Application log | 30 ngày |

## 6. Khả năng quan sát

Mỗi request và event phải mang:

- `trace_id` và `span_id`.
- `correlation_id` được trả cho client.
- `tenant_id` dưới dạng metadata được kiểm soát, không chứa raw PII.
- Thuộc tính service/version/environment.

Metric bắt buộc:

- Số request HTTP, thời gian và tỷ lệ lỗi.
- Mức sử dụng DB pool và thời gian query.
- Queue depth, consumer lag, redelivery và số lượng DLQ.
- AI job theo type/status.
- Model latency, token, chi phí ước tính và lỗi schema validation.
- Retrieval latency, số kết quả và tỷ lệ không có kết quả.

## 7. Khả năng bảo trì

- Java 21 và một dòng Spring Boot minor được hỗ trợ.
- Public API và event phải version rõ ràng.
- Thay đổi database dùng forward-only Flyway migration.
- Mỗi service có line coverage >= 70% làm guardrail; domain quan trọng cần behavior-focused test.
- Architecture test kiểm soát dependency giữa các package.
