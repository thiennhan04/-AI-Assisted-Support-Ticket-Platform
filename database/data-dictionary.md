# Data Dictionary and Ownership

## 1. General rules

- UUIDs are generated application-side using UUIDv7 when supported; UUIDv4 is acceptable.
- All timestamps are `timestamptz` UTC.
- Entity tables include optimistic-lock `version` when user-modifiable.
- `tenant_id` is mandatory on tenant-owned tables even when derivable; this supports safe indexed predicates.
- Foreign keys do not cross service databases. IDs from another service are references validated by claims/API/events.
- JSONB is limited to immutable snapshots or flexible metadata, not core searchable state.

## 2. Identity database

| Table | Purpose | Sensitive fields | Deletion |
|---|---|---|---|
| `tenant` | Tenant lifecycle | none | suspend before deletion |
| `app_user` | Login identity | email, password hash | anonymize or retain per policy |
| `user_role` | Tenant role membership | none | cascade with user |
| `refresh_session` | Rotation/revocation | token hash, IP/agent if added | purge after expiry + 30 days |

## 3. Ticket database

| Table | Purpose | Update pattern |
|---|---|---|
| `ticket` | Ticket aggregate root | optimistic locking |
| `ticket_comment` | Append-only conversation entries | never edit; correction adds new entry |
| `ticket_attachment` | Object metadata/status | lifecycle transitions |
| `ticket_ai_analysis` | AI result projection | upsert once per job |
| `ticket_ai_draft` | Draft projection/approval | immutable result, approval metadata only |
| `ticket_audit` | Append-only audit trail | insert only |
| `idempotency_record` | Command replay protection | TTL cleanup |
| `outbox_event` | Reliable publication | mark published |
| `processed_event` | Consumer deduplication | TTL >= source replay window |

## 4. AI database

`ai_job.input_payload` may contain ticket content and is sensitive. Apply 30-day retention or redact after terminal completion while retaining hashes/metrics. `prompt_template` is configuration audit data and should not be hard-deleted after use. `model_usage` contains no raw prompt/output.

## 5. Knowledge database

`knowledge_document` is stable identity. `document_version` represents every ingestion attempt/version. `document_chunk` belongs to one immutable version. Only chunks belonging to the document's `active_version_id` are searchable.

Vector dimensions are schema-bound. Do not silently insert embeddings from a model with a different dimension.

## 6. Migration strategy

- Each service owns `V001__baseline.sql`, subsequent forward migrations and its Flyway history.
- Never edit an applied migration.
- Destructive changes use expand-migrate-contract across releases.
- Large index builds use online/concurrent operation where supported.
- Seed only reference values; test/demo users belong in a local profile script.

