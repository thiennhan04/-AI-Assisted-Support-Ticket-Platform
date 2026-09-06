# Identity Service - Detail Design

## 1. Responsibilities

- Register/invite users.
- Authenticate email/password.
- Issue short-lived access JWT and rotating refresh token.
- Revoke sessions and expose JWKS public keys.
- Manage tenant-scoped roles: `CUSTOMER`, `AGENT`, `ADMIN`.

Out of scope: ticket ownership, document ACL and UI profile preferences.

## 2. Package structure

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

Dependencies point inward: `api -> application -> domain`; infrastructure implements domain ports.

## 3. Endpoints

### `POST /v1/auth/login`

Request: `{ "email": "agent@example.com", "password": "..." }`

Response `200`: access token, refresh token, `expiresIn`, user summary. Generic `401 AUTH_INVALID_CREDENTIALS` for unknown user or wrong password.

### `POST /v1/auth/refresh`

Accepts one refresh token. Rotate on every use: mark old token replaced and issue a new token family member. Reuse of an already rotated token revokes the entire family and returns `401 AUTH_REFRESH_REUSE_DETECTED`.

### `POST /v1/auth/logout`

Revokes current refresh session. Idempotent; returns `204`.

### `GET /.well-known/jwks.json`

Returns active and previous public keys. Cache control: five minutes. Key ID (`kid`) is mandatory.

### Admin users

- `POST /v1/admin/users`
- `GET /v1/admin/users`
- `PATCH /v1/admin/users/{id}/roles`
- `PATCH /v1/admin/users/{id}/status`

All require `ADMIN` and same tenant.

## 4. JWT claims

| Claim | Meaning |
|---|---|
| `sub` | user UUID |
| `tid` | tenant UUID |
| `roles` | string array |
| `iss` | configured issuer |
| `aud` | `ticket-platform` |
| `jti` | token UUID |
| `iat`, `exp` | issued/expiry time |

Access TTL: 15 minutes. Refresh TTL: 30 days. Services reject missing `tid`, invalid audience or unknown signing key.

## 5. Persistence

Use tables `tenant`, `app_user`, `user_role`, `refresh_session`, `outbox_event`. Email uniqueness is case-insensitive per tenant. Store refresh token as SHA-256 hash, never plaintext.

## 6. Transaction rules

- Login verification and refresh-session creation occur in one transaction after password verification.
- Token signing occurs outside the transaction; if it fails, session creation must roll back or be revoked.
- Role/status updates increment `app_user.version` and publish `identity.user.changed.v1` through outbox.

## 7. Security details

- Rate limit login by normalized email hash and IP: 10 attempts/15 minutes.
- Add uniform 150-300 ms randomized delay to failed login.
- Disable after configurable repeated failures; unlock through admin flow.
- Never log password, access token or refresh token.
- Clock skew allowance: 60 seconds.

## 8. Tests

- Successful login and claim assertions.
- Wrong password and unknown email return identical response.
- Refresh rotation and family reuse detection.
- Revoked/disabled user cannot refresh.
- Cross-tenant admin access denied.
- JWKS contains current/previous keys.
- Concurrent refresh permits exactly one success.

