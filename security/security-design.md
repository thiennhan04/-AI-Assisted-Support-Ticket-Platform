# Security Design and Threat Model

## 1. Trust boundaries

1. Browser/client to public gateway: untrusted network and input.
2. Gateway to services: authenticated user token; services still authorize independently.
3. Service-to-service: private network plus workload credential/mTLS when available.
4. Platform to model provider: external data-processing boundary.
5. Platform to object storage: untrusted uploaded bytes.
6. Retrieved knowledge content to LLM: untrusted instructions embedded in documents.

## 2. Authentication

- Public APIs require RS256 access JWT except login, refresh and JWKS.
- Validate signature, issuer, audience, expiry and mandatory `sub`/`tid` claims.
- Internal endpoints reject user tokens and require service identity with expected audience/scope.
- Signing private keys exist only in Identity Service secret store.
- Rotation overlaps current and previous public key until maximum token TTL expires.

## 3. Authorization matrix

| Action | Customer | Agent | Admin |
|---|:---:|:---:|:---:|
| Create ticket | own | any requester in tenant | yes |
| View ticket | own | tenant | tenant |
| Assign/change priority | no | yes | yes |
| Internal comment | no | yes | yes |
| Request/approve AI draft | no | yes | yes |
| Upload knowledge | no | no | yes |
| View agent-only knowledge | no | yes | yes |
| Manage users | no | no | yes |

Controller annotations are defense-in-depth. Application services must call policy objects with authenticated principal and resource tenant/ownership.

## 4. Tenant isolation

- Repository methods require `tenantId`; do not offer unscoped `findById` in application code.
- Derive tenant from verified token, never request body/query.
- Login is the only tenant lookup exception: it accepts a normalized `tenantCode` because no verified
  token exists yet. Identity resolves the code to a tenant UUID and writes that UUID to the signed `tid`
  claim. All authenticated endpoints continue to derive tenant exclusively from the token.
- Cross-tenant resources return 404 where enumeration is possible.
- Add integration tests that create identical-shaped data in two tenants.
- Production may add PostgreSQL Row-Level Security as a second layer; application predicates remain mandatory.

## 5. AI-specific threats

### Prompt injection from ticket or document

Controls:

- Treat all user/document text as data enclosed by explicit delimiters.
- System prompt states retrieved text cannot override policy or call unauthorized tools.
- Tool availability is server-defined; model cannot construct arbitrary URLs or SQL.
- Retrieved documents are never granted more access because the model asks.
- Limit tool arguments through typed schemas and server validation.

### Data leakage

- Run tenant/ACL filter before retrieval/ranking result leaves Knowledge Service.
- Redact configured sensitive values before provider call.
- Do not log prompts or completions by default.
- Use provider data-retention settings consistent with project policy.
- Never place API keys, internal prompts or hidden metadata in retrieved content.

### Hallucination and unsafe automation

- AI suggestions do not mutate accepted category/priority automatically in v1.
- AI drafts require human approval.
- Grounded answers validate citation IDs against retrieved set.
- No-knowledge result is acceptable; do not fall back to invented instructions.

### Denial of wallet/service

- Per-tenant daily token/cost budget.
- Maximum input/output/context tokens.
- Request rate limits and bounded worker concurrency.
- Reject oversized documents and recursive archives.
- Circuit breaker and queue backpressure.

## 6. File upload controls

- Allowlist PDF, DOCX and plain text by detected content, not filename alone.
- Maximum 25 MB, 500 pages.
- Opaque object key; original filename stored only as metadata after sanitization.
- SHA-256 verification.
- Malware scan before activation/download.
- Reject password-protected/encrypted files in v1.
- Signed URL expires after 15 minutes and is limited to one object key/content length.

## 7. API protection

- Validate all request DTOs and reject unknown security-sensitive fields.
- CORS allowlist by environment.
- CSRF not required for Authorization-header bearer tokens stored outside cookies; if refresh token uses secure cookie, add CSRF protection.
- Rate-limit login, upload creation, ticket creation and AI draft endpoints separately.
- Return safe problem details; log internal cause with correlation ID.

## 8. Logging policy

Never log:

- passwords or token values.
- full ticket descriptions/comments in production.
- raw prompts/completions.
- signed object URLs.
- document chunks or embedding vectors.

Allow controlled identifiers: tenant ID, ticket ID, job ID, model alias, token counts, status/error code.

## 9. Security acceptance tests

- JWT algorithm-confusion and wrong-audience rejection.
- Expired/revoked session behavior.
- Every cross-tenant endpoint case.
- Restricted knowledge cannot be inferred through score/count/error differences.
- Prompt injection fixture cannot activate tools or expose system instructions.
- Hallucinated citation is rejected.
- Malicious filename and MIME mismatch rejected.
- Rate limit returns 429 with `Retry-After`.
