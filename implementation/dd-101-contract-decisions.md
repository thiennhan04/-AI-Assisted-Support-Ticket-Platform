# DD-101 Contract Decisions

## Login tenant context

`POST /v1/auth/login` accepts `tenantCode`, `email`, and `password`. Email is unique only
inside a tenant, so Identity resolves the normalized tenant code before selecting the user.
This is the only endpoint allowed to accept tenant lookup context from an unauthenticated
request. Authenticated APIs derive tenant exclusively from the verified JWT `tid` claim.

Unknown, suspended, locked or disabled tenants/users and an invalid password all return the
same `401 AUTH_INVALID_CREDENTIALS` response.

## Token response and story boundary

Login returns an RS256 access JWT, an opaque refresh token, `Bearer`, the access TTL in
seconds, and a user summary. DD-101 creates the first refresh session and stores only the
SHA-256 token hash. DD-102 owns refresh rotation, reuse detection and logout.

Access JWTs live for 15 minutes and contain `sub`, `tid`, `roles`, `iss`, `aud`, `jti`,
`iat`, and `exp`. The protected JWT header contains `alg=RS256` and a mandatory `kid`.

## Transaction boundary

Password verification and token generation happen before a short write transaction. That
transaction locks and revalidates the user, resets failed attempts, and persists the refresh
session hash. Tokens are returned only after commit. Failed-attempt updates use an independent
transaction so the generic authentication exception cannot roll them back.

## Login protection

Five consecutive password failures temporarily lock a user for 15 minutes. A successful login
resets the counter. `DISABLED` is an explicit administrator state. Redis limits attempts by a
SHA-256 hash of normalized tenant/email and separately by the trusted remote IP. Default local
limits are 10 attempts per 15 minutes, and rejected attempts return `429` with `Retry-After`.

## Tenant-safe persistence

`user_role` and `refresh_session` carry `tenant_id`. Composite foreign keys prevent a role or
session from referencing a user in another tenant. Repository operations used by application
code always include tenant context.
