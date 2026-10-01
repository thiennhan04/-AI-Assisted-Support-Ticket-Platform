# Checklist trước khi developer bắt đầu

Dùng danh sách này trước khi code một story.

## Thiết lập platform

- [ ] Đã cài Java 21 và container runtime.
- [ ] Hạ tầng local healthy.
- [ ] Đã tạo database/user của service và áp dụng Flyway baseline.
- [ ] Đã chọn fake AI/embedding provider.
- [ ] Đã import OpenAPI và có event sample.

## Với mỗi endpoint

- [ ] Đã xác định role và tenant policy.
- [ ] Đã xác định request limit/validation.
- [ ] Đã quyết định idempotency và optimistic concurrency.
- [ ] Đã xác định transaction boundary.
- [ ] Đã xác định yêu cầu audit/outbox.
- [ ] Đã ghi problem code.
- [ ] Đã viết unit, security và integration case.

## Với mỗi event

- [ ] Đã xác định producer transaction/outbox.
- [ ] Đã đăng ký routing key và payload version.
- [ ] Đã giảm thiểu PII.
- [ ] Đã xác định consumer idempotency key/transaction.
- [ ] Đã phân loại lỗi retryable/permanent.
- [ ] Đã ghi hành vi DLQ và replay.
- [ ] Có test delivery thông thường và delivery trùng.

## Với mỗi tính năng AI

- [ ] Business fallback hoạt động khi không có AI.
- [ ] Đã xác định input/output schema và giới hạn.
- [ ] Prompt version bất biến/có audit.
- [ ] Đã kiểm tra data classification/redaction.
- [ ] Grounded claim/citation được kiểm tra khi cần.
- [ ] Có điểm human approval rõ ràng.
- [ ] Đo token, chi phí, độ trễ và chất lượng.
- [ ] Thêm evaluation case trước khi chấp nhận thay đổi prompt.

## Hoàn tất pull request

- [ ] Build, unit, integration, architecture và contract test thành công.
- [ ] Migration được test từ schema sạch và schema trước đó.
- [ ] Metric/log không chứa raw user content hoặc secret.
- [ ] Đã cập nhật tài liệu API/event.
- [ ] Đã nêu hành vi rollback/degradation.
- [ ] Chứng minh acceptance criteria bằng test hoặc ảnh/log.
