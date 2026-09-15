# DD-102 Implementation Note

## Delivered scope

- Added `POST /v1/auth/refresh` with single-use refresh-token rotation.
- Added `POST /v1/auth/logout` with idempotent token-family revocation.
- Added pessimistic locking for refresh-session lookup and atomic replacement linking.
- Added reuse detection that commits family revocation before returning
  `AUTH_REFRESH_REUSE_DETECTED`.
- Rejected unknown, expired, revoked, suspended-tenant, disabled-user, invited-user, and actively
  locked-user sessions with `AUTH_INVALID_REFRESH_TOKEN`.
- Added `Cache-Control: no-store` and `Pragma: no-cache` to login and refresh responses.

## Persistence

- Expanded the refresh-session domain/JPA mapping to include `replaced_by_id`, `revoked_at`, and
  `last_used_at`.
- Added Flyway `V002__refresh_rotation_constraints.sql` with a self-referencing replacement
  foreign key and a partial unique replacement index.
- Kept raw refresh tokens outside PostgreSQL; lookup and persistence use only SHA-256 hashes.

## Contract and design

- Added the logout endpoint and reusable strict `RefreshTokenRequest` schema to OpenAPI.
- Updated the Identity and security designs with rotation, reuse, logout, transaction, and
  no-store behavior.
- Captured detailed choices in `implementation/dd-102-contract-decisions.md`.

## Verification performed

- Full monorepo `./mvnw.cmd -B clean verify`: all seven modules passed Enforcer, compilation,
  formatting, unit-test, integration-test, and packaging phases.
- Identity unit tests: 8 passed.
- Identity Testcontainers integration tests: 15 passed against PostgreSQL and Redis.
- The concurrency test launched two refresh requests for one token and confirmed exactly one
  `200`, one `401 AUTH_REFRESH_REUSE_DETECTED`, and no active row left in the family.
- Clean-database Flyway execution successfully applied `V001` and `V002`, followed by Hibernate
  schema validation.
- Local runtime smoke test reached Flyway `v002` and passed login, rotation, idempotent logout,
  post-logout rejection, and reuse-family revocation over real HTTP.
- Rebuilt `ticket-platform/identity-service:0.1.0-SNAPSHOT` successfully with Spring Boot
  Buildpacks after verification.

The local PostgreSQL volume now includes migration `v002` and refresh-session rows produced by the
smoke test. PostgreSQL and Redis were left running; the temporary Identity JVM was stopped.
