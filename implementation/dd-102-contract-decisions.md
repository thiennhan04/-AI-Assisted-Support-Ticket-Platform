# DD-102 Contract Decisions

## Refresh request and response

`POST /v1/auth/refresh` accepts `{ "refreshToken": "..." }` without an access JWT. The
refresh token itself is the bearer credential, which allows a client to recover after its
short-lived access token expires. Tokens are transported in JSON rather than cookies, so the
current API does not require cookie-oriented CSRF protection.

Every successful call returns the same token response shape as login, rotates the refresh token,
and extends the new session expiry by the configured refresh TTL. Login and refresh responses use
`Cache-Control: no-store` and `Pragma: no-cache`.

## Rotation and reuse

A refresh token is single-use. Rotation creates a new row with the same `family_id`, writes the
new row's ID to the old row's `replaced_by_id`, and records `last_used_at`. The database enforces
that a replacement session exists and cannot be referenced as the replacement of multiple rows.

Presenting an already rotated token is reuse, even if it has since expired. Reuse revokes every
row in the family and returns `401 AUTH_REFRESH_REUSE_DETECTED`. Unknown, expired, revoked, or
ineligible-user tokens return `401 AUTH_INVALID_REFRESH_TOKEN`.

## Concurrency and transaction boundary

Refresh selects the presented session using a pessimistic write lock. Concurrent requests for the
same token serialize: the first rotates it and the second observes `replaced_by_id`, revokes the
family, and fails. The reuse exception is configured not to roll back its family-revocation write.

JWT signing and refresh-token generation are local operations and occur inside this short
transaction. No network or broker call is made while the database lock is held. A token response
is visible to the caller only after commit.

## Logout

`POST /v1/auth/logout` accepts the same refresh-token request and revokes the submitted token's
entire family. Logout returns `204` for known, unknown, previously rotated, or already revoked
tokens. This makes logout idempotent and avoids disclosing whether a token exists.
