# Ghi chú triển khai DD-101

## Phạm vi đã bàn giao

- Triển khai `POST /v1/auth/login` có nhận biết tenant bằng `tenantCode`, email đã chuẩn hóa và password.
- Thêm kiểm tra BCrypt password, lỗi xác thực chung, tạm khóa sau năm lần thất bại và giới hạn bằng Redis theo tenant/email cùng remote IP.
- Phát access JWT RS256 15 phút chứa `sub`, `tid`, `roles`, `iss`, `aud`, `jti`, `iat`, `exp`.
- Tạo refresh token opaque và chỉ lưu SHA-256 hash. Rotation, phát hiện reuse và logout thuộc phạm vi DD-102.
- Công bố public RSA key hiện tại và tùy chọn key trước đó tại `/.well-known/jwks.json`; không bao giờ lộ private key.
- Thêm problem response kiểu RFC 9457 cho credential sai, validation failure và rate limit.

## Persistence và cô lập tenant

- Thêm Flyway migration `V001__identity_baseline.sql` cho `identity.tenant`, `identity.app_user`, `identity.user_role`, `identity.refresh_session`.
- Email duy nhất theo `(tenant_id, email)`, nên cùng email có thể tồn tại ở tenant khác nhau.
- Role và refresh session có `tenant_id`; composite foreign key ngăn tham chiếu chéo tenant.
- Ghi thành công dùng transaction ngắn và pessimistic revalidation; cập nhật lần thất bại dùng transaction độc lập để authentication exception không rollback.

## Phát triển local

- Thêm script tạo RSA key tương thích Windows PowerShell tại `scripts/generate-local-rsa-keys.ps1`. Key được ghi dưới `.local/keys/` đã bị ignore.
- Thêm profile `local` cho PostgreSQL, Redis, Flyway validation, vị trí RSA key và tùy chọn seed tenant/user có tính xác định.
- Cập nhật `.env.example` và `ops/local-development.md` với luồng khởi động Identity và login.
- Tắt RabbitMQ health cho Identity trong DD-101 vì story này không phát/nhận AMQP message. Bật lại trong story phát event.

## Cập nhật contract và thiết kế

- Cập nhật `contracts/openapi.yaml`, `services/identity-service.md`, `security/security-design.md`, `database/ddl.sql` theo contract tenant/token đã triển khai.
- Ghi lại quyết định và ranh giới DD-102 tại `implementation/dd-101-contract-decisions.md`.

## Kiểm tra đã thực hiện

- `./mvnw.cmd -B clean verify`: cả bảy module đều thành công.
- Identity unit test: 5 test thành công.
- Identity integration test dùng Testcontainers: 9 test thành công với PostgreSQL và Redis sạch; bao phủ login thành công, cô lập tenant, lỗi chung, khóa tài khoản, principal suspended/disabled, rate limit, input nghiêm ngặt, JWKS, token claim/hash storage và từ chối sai issuer/audience/signature.
- Local smoke test: Flyway đạt `v001`; health trả `UP`; login bằng dữ liệu seed trả token có tenant và xác minh được; JWKS trả đúng `kid`, cache policy và không có private exponent.
- Tạo OCI image thành công: `ticket-platform/identity-service:0.1.0-SNAPSHOT` (image ID prefix `f2afac9d6691`).

PostgreSQL volume local hiện chứa schema DD-101, tenant/user demo và refresh session từ smoke test. Container PostgreSQL và Redis vẫn chạy; JVM Identity tạm đã được dừng.
