# Ghi chú triển khai DD-102

## Phạm vi đã bàn giao

- Thêm `POST /v1/auth/refresh` với cơ chế rotation refresh token dùng một lần.
- Thêm `POST /v1/auth/logout` thu hồi token family theo cách idempotent.
- Thêm pessimistic locking khi tìm refresh session và liên kết replacement nguyên tử.
- Thêm phát hiện reuse, commit việc thu hồi family trước khi trả `AUTH_REFRESH_REUSE_DETECTED`.
- Từ chối session không tồn tại, hết hạn, đã thu hồi, tenant suspended, user disabled/invited/đang bị khóa bằng `AUTH_INVALID_REFRESH_TOKEN`.
- Thêm `Cache-Control: no-store` và `Pragma: no-cache` cho response login và refresh.

## Persistence

- Mở rộng domain/JPA mapping của refresh session với `replaced_by_id`, `revoked_at`, `last_used_at`.
- Thêm Flyway `V002__refresh_rotation_constraints.sql` với self-referencing replacement foreign key và partial unique replacement index.
- Không lưu raw refresh token trong PostgreSQL; tra cứu và persistence chỉ dùng SHA-256 hash.

## Contract và thiết kế

- Thêm logout endpoint và strict schema `RefreshTokenRequest` dùng lại được vào OpenAPI.
- Cập nhật thiết kế Identity và security với rotation, reuse, logout, transaction và hành vi no-store.
- Ghi lại lựa chọn chi tiết tại `implementation/dd-102-contract-decisions.md`.

## Kiểm tra đã thực hiện

- Toàn monorepo `./mvnw.cmd -B clean verify`: cả bảy module vượt qua Enforcer, compile, format, unit test, integration test và package.
- Identity unit test: 8 test thành công.
- Identity integration test dùng Testcontainers: 15 test thành công với PostgreSQL và Redis.
- Concurrency test gửi hai refresh request cho một token và xác nhận đúng một response `200`, một response `401 AUTH_REFRESH_REUSE_DETECTED`, không còn row active trong family.
- Flyway chạy trên database sạch, áp dụng thành công `V001`, `V002`, sau đó Hibernate schema validation thành công.
- Local smoke test đạt Flyway `v002` và kiểm tra thành công login, rotation, logout idempotent, từ chối sau logout, thu hồi family khi reuse qua HTTP thật.
- Buildpack tạo lại thành công `ticket-platform/identity-service:0.1.0-SNAPSHOT`.

PostgreSQL volume local hiện có migration `v002` và các refresh-session row từ smoke test. PostgreSQL và Redis vẫn chạy; JVM Identity tạm đã dừng.
