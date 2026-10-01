# Thiết kế chi tiết Identity Service

## 1. Trách nhiệm

- Đăng ký/mời user và quản lý role theo tenant: `CUSTOMER`, `AGENT`, `ADMIN`.
- Xác thực email/password.
- Phát access JWT ngắn hạn và rotating refresh token.
- Thu hồi session và công bố JWKS public key.

Không thuộc phạm vi: ticket ownership, document ACL và tùy chọn UI profile.

## 2. Cấu trúc package

```text
com.portfolio.identity
  api
    AuthController
    UserAdminController
    dto
    advice
  application
    AuthService
    UserService
    TokenService
  domain
    User
    RefreshSession
    Role
    UserRepository
  infrastructure
    persistence
    security
    messaging
    config
```

Dependency hướng vào trong: `api -> application -> domain`; infrastructure hiện thực domain port.

## 3. Endpoint

### `POST /v1/auth/login`

Request: `{ "tenantCode": "acme", "email": "agent@example.com", "password": "..." }`.
`tenantCode` là lookup context khi chưa xác thực vì email chỉ unique trong tenant. Sau login, service
chỉ lấy tenant từ claim JWT `tid` đã xác minh.

Response `200` gồm access token, refresh token, `expiresIn` và user summary. User không tồn tại hoặc
sai password đều trả `401 AUTH_INVALID_CREDENTIALS` giống nhau.

### `POST /v1/auth/refresh`

Nhận `{ "refreshToken": "..." }`. Mỗi lần dùng phải rotate trong transaction ngắn: lock session,
đánh dấu replaced và tạo thành viên mới trong token family. Dùng lại token đã rotate sẽ revoke cả
family và trả `401 AUTH_REFRESH_REUSE_DETECTED`. Token lạ, hết hạn, revoked hoặc user không hợp lệ trả
`401 AUTH_INVALID_REFRESH_TOKEN`.

### `POST /v1/auth/logout`

Nhận `{ "refreshToken": "..." }` và revoke toàn bộ family. Endpoint idempotent, luôn trả `204` với
request đúng định dạng kể cả token lạ hoặc đã revoke.

### `GET /.well-known/jwks.json`

Trả public key hiện tại và trước đó, cache năm phút; bắt buộc có key ID (`kid`).

### Quản trị user

- `POST /v1/admin/users`
- `GET /v1/admin/users`
- `PATCH /v1/admin/users/{id}/roles`
- `PATCH /v1/admin/users/{id}/status`

Tất cả yêu cầu `ADMIN` và cùng tenant.

## 4. JWT claim

| Claim | Ý nghĩa |
|---|---|
| `sub` | user UUID |
| `tid` | tenant UUID |
| `roles` | mảng string |
| `iss` | issuer đã cấu hình |
| `aud` | `ticket-platform` |
| `jti` | token UUID |
| `iat`, `exp` | thời điểm phát/hết hạn |

Access TTL 15 phút, refresh TTL 30 ngày. Service từ chối thiếu `tid`, sai audience hoặc signing key
không biết.

## 5. Persistence

Dùng các bảng `tenant`, `app_user`, `user_role`, `refresh_session`, `outbox_event`. Email unique không
phân biệt hoa thường trong tenant. Chỉ lưu SHA-256 hash của refresh token, không lưu plaintext.

## 6. Quy tắc transaction

- Sau khi kiểm tra password, xác minh login và tạo refresh session trong một transaction.
- Sinh/ký token trước write transaction ngắn. Transaction lock và kiểm tra lại user, reset failed
  attempt và chỉ lưu refresh-token hash. Chỉ trả token sau commit.
- Refresh dùng pessimistic lock; chỉ một request đồng thời được rotate. Reuse detection phải commit
  việc revoke cả family trước khi trả lỗi bảo mật.
- Đổi role/status tăng `app_user.version` và phát `identity.user.changed.v1` qua outbox.

## 7. Chi tiết bảo mật

- Rate limit login theo normalized email hash và IP: 10 lần/15 phút.
- Thêm độ trễ ngẫu nhiên 150–300 ms khi login lỗi.
- Khóa tạm 15 phút sau năm lần sai liên tiếp. `DISABLED` là trạng thái Admin đặt và không tự xóa.
- Không log password, access token hoặc refresh token; cho phép clock skew 60 giây.

## 8. Kiểm thử

- Login thành công và claim đúng.
- Sai password/email lạ trả response giống nhau.
- Refresh rotation, family reuse detection và concurrent refresh chỉ một thành công.
- Người dùng bị revoked/disabled không refresh được.
- Từ chối Admin cross-tenant.
- JWKS chứa key hiện tại/trước đó.
