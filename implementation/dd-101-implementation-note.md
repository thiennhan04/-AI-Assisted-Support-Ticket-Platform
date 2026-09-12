# DD-101 Implementation Note

## Delivered scope

- Implemented tenant-aware `POST /v1/auth/login` using `tenantCode`, normalized email, and
  password.
- Added BCrypt password verification, generic authentication failures, temporary account
  locking after five failures, and Redis limits by tenant/email plus remote IP.
- Issued 15-minute RS256 access JWTs containing `sub`, `tid`, `roles`, `iss`, `aud`, `jti`,
  `iat`, and `exp`.
- Generated opaque refresh tokens and persisted only their SHA-256 hashes. Rotation, reuse
  detection, and logout remain DD-102 scope.
- Published current and optional previous public RSA keys at `/.well-known/jwks.json`; private
  key material is never exposed.
- Added RFC 9457-style problem responses for invalid credentials, validation failures, and
  rate limiting.

## Persistence and tenant isolation

- Added Flyway migration `V001__identity_baseline.sql` for `identity.tenant`,
  `identity.app_user`, `identity.user_role`, and `identity.refresh_session`.
- Email uniqueness is `(tenant_id, email)`, so the same email may exist in different tenants.
- Roles and refresh sessions include `tenant_id`, with composite foreign keys preventing
  cross-tenant references.
- Success writes use a short transaction and pessimistic revalidation; failed-attempt updates
  use an independent transaction so the authentication exception does not roll them back.

## Local development

- Added a Windows PowerShell-compatible RSA key generator at
  `scripts/generate-local-rsa-keys.ps1`. Keys are written below ignored `.local/keys/`.
- Added the `local` profile for PostgreSQL, Redis, Flyway validation, RSA key locations, and an
  opt-in deterministic tenant/user seed.
- Updated `.env.example` and `ops/local-development.md` with the Identity startup and login
  workflow.
- RabbitMQ health is disabled for Identity during DD-101 because this story does not publish or
  consume AMQP messages. It should be enabled with the event-producing story.

## Contract and design updates

- Updated `contracts/openapi.yaml`, `services/identity-service.md`,
  `security/security-design.md`, and `database/ddl.sql` to match the implemented tenant and
  token contracts.
- Captured the decisions and DD-102 boundary in `implementation/dd-101-contract-decisions.md`.

## Verification performed

- `./mvnw.cmd -B clean verify`: passed for all seven modules.
- Identity unit tests: 5 passed.
- Identity Testcontainers integration tests: 9 passed against clean PostgreSQL and Redis
  containers. They cover successful login, tenant isolation, generic failures, account lock,
  suspended/disabled principals, rate limiting, strict input, JWKS, token claims/hash storage,
  and rejection of wrong issuer, audience, or signature.
- Local runtime smoke test: Flyway reached `v001`; health returned `UP`; seeded login returned a
  verifiable tenant-aware token; JWKS returned the expected `kid`, cache policy, and no private
  exponent.
- OCI image built successfully as
  `ticket-platform/identity-service:0.1.0-SNAPSHOT` (image ID prefix `f2afac9d6691`).

The local PostgreSQL volume now contains the DD-101 schema, demo tenant/users, and refresh
sessions created by the smoke test. PostgreSQL and Redis containers were left running; the
temporary Identity JVM was stopped.
