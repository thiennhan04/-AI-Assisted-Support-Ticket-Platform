-- Reference DDL. Split into service-owned Flyway migrations before implementation.
-- Production uses separate databases. Schema prefixes below make local inspection easier.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS ticket;
CREATE SCHEMA IF NOT EXISTS ai;
CREATE SCHEMA IF NOT EXISTS knowledge;

CREATE TABLE identity.tenant (
  id uuid PRIMARY KEY,
  code varchar(50) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE identity.app_user (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES identity.tenant(id),
  email citext NOT NULL,
  password_hash varchar(255) NOT NULL,
  display_name varchar(200) NOT NULL,
  status varchar(20) NOT NULL CHECK (status IN ('INVITED','ACTIVE','LOCKED','DISABLED')),
  failed_login_count integer NOT NULL DEFAULT 0,
  locked_until timestamptz,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, email)
);

CREATE TABLE identity.user_role (
  user_id uuid NOT NULL REFERENCES identity.app_user(id) ON DELETE CASCADE,
  role varchar(20) NOT NULL CHECK (role IN ('CUSTOMER','AGENT','ADMIN')),
  PRIMARY KEY (user_id, role)
);

CREATE TABLE identity.refresh_session (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES identity.app_user(id),
  family_id uuid NOT NULL,
  token_hash char(64) NOT NULL UNIQUE,
  replaced_by_id uuid,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz
);
CREATE INDEX ix_refresh_user ON identity.refresh_session(user_id, expires_at);

CREATE TABLE ticket.ticket (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_number varchar(30) NOT NULL,
  requester_id uuid NOT NULL,
  assignee_id uuid,
  subject varchar(300) NOT NULL,
  description text NOT NULL,
  category varchar(30),
  priority varchar(20) NOT NULL,
  status varchar(30) NOT NULL,
  content_version bigint NOT NULL DEFAULT 0,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  closed_at timestamptz,
  UNIQUE (tenant_id, ticket_number),
  CHECK (char_length(description) <= 20000)
);
CREATE INDEX ix_ticket_status_updated ON ticket.ticket(tenant_id, status, updated_at DESC);
CREATE INDEX ix_ticket_assignee_status ON ticket.ticket(tenant_id, assignee_id, status);
CREATE INDEX ix_ticket_requester_created ON ticket.ticket(tenant_id, requester_id, created_at DESC);
CREATE INDEX ix_ticket_search ON ticket.ticket USING gin
  (to_tsvector('simple', coalesce(subject,'') || ' ' || coalesce(description,'')));

CREATE TABLE ticket.ticket_comment (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_id uuid NOT NULL REFERENCES ticket.ticket(id),
  author_id uuid NOT NULL,
  body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 10000),
  internal boolean NOT NULL DEFAULT false,
  source varchar(20) NOT NULL CHECK (source IN ('HUMAN','AI_DRAFT_APPROVED')),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_comment_ticket_created ON ticket.ticket_comment(tenant_id, ticket_id, created_at);

CREATE TABLE ticket.ticket_attachment (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_id uuid NOT NULL REFERENCES ticket.ticket(id),
  object_key varchar(500) NOT NULL UNIQUE,
  original_name varchar(255) NOT NULL,
  mime_type varchar(150) NOT NULL,
  size_bytes bigint NOT NULL,
  sha256 char(64),
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ticket.ticket_ai_analysis (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_id uuid NOT NULL REFERENCES ticket.ticket(id),
  ai_job_id uuid NOT NULL UNIQUE,
  input_content_version bigint NOT NULL,
  status varchar(20) NOT NULL,
  summary text,
  suggested_category varchar(30),
  suggested_priority varchar(20),
  rationale text,
  prompt_version varchar(100),
  model varchar(100),
  stale boolean NOT NULL DEFAULT false,
  generated_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ticket.ticket_ai_draft (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_id uuid NOT NULL REFERENCES ticket.ticket(id),
  ai_job_id uuid NOT NULL UNIQUE,
  input_content_version bigint NOT NULL,
  status varchar(20) NOT NULL,
  reply text,
  citations jsonb NOT NULL DEFAULT '[]',
  risk_flags jsonb NOT NULL DEFAULT '[]',
  prompt_version varchar(100),
  model varchar(100),
  created_at timestamptz NOT NULL DEFAULT now(),
  generated_at timestamptz,
  approved_at timestamptz,
  approved_by uuid
);

CREATE TABLE ticket.ticket_audit (
  id bigserial PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_id uuid NOT NULL,
  actor_id uuid,
  action varchar(80) NOT NULL,
  old_value jsonb,
  new_value jsonb,
  correlation_id varchar(100),
  occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_audit ON ticket.ticket_audit(tenant_id, ticket_id, occurred_at);

CREATE TABLE ticket.idempotency_record (
  tenant_id uuid NOT NULL,
  idempotency_key uuid NOT NULL,
  request_hash char(64) NOT NULL,
  resource_id uuid,
  response_status integer NOT NULL,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, idempotency_key)
);

CREATE TABLE ai.ai_job (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  job_type varchar(40) NOT NULL,
  subject_type varchar(40) NOT NULL,
  subject_id uuid NOT NULL,
  input_hash char(64) NOT NULL,
  input_payload jsonb NOT NULL,
  status varchar(30) NOT NULL,
  prompt_template_id uuid,
  prompt_version varchar(100),
  model varchar(100),
  attempt_count integer NOT NULL DEFAULT 0,
  output_payload jsonb,
  error_code varchar(100),
  started_at timestamptz,
  completed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, job_type, subject_id, input_hash)
);
CREATE INDEX ix_ai_job_status ON ai.ai_job(status, created_at);

CREATE TABLE ai.prompt_template (
  id uuid PRIMARY KEY,
  tenant_id uuid,
  job_type varchar(40) NOT NULL,
  name varchar(100) NOT NULL,
  semantic_version varchar(30) NOT NULL,
  system_template text NOT NULL,
  user_template text NOT NULL,
  output_schema jsonb NOT NULL,
  checksum char(64) NOT NULL,
  status varchar(20) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, job_type, semantic_version)
);

CREATE TABLE ai.model_usage (
  id bigserial PRIMARY KEY,
  ai_job_id uuid NOT NULL REFERENCES ai.ai_job(id),
  provider varchar(50) NOT NULL,
  model varchar(100) NOT NULL,
  input_tokens integer,
  output_tokens integer,
  latency_ms integer NOT NULL,
  estimated_cost_usd numeric(12,6),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ai.ai_feedback (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ai_job_id uuid NOT NULL,
  user_id uuid NOT NULL,
  rating smallint NOT NULL CHECK (rating BETWEEN 1 AND 5),
  reason varchar(1000),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (ai_job_id, user_id)
);

CREATE TABLE knowledge.knowledge_document (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  title varchar(300) NOT NULL,
  visibility varchar(30) NOT NULL,
  tags jsonb NOT NULL DEFAULT '[]',
  status varchar(30) NOT NULL,
  active_version_id uuid,
  created_by uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE knowledge.document_version (
  id uuid PRIMARY KEY,
  document_id uuid NOT NULL REFERENCES knowledge.knowledge_document(id),
  version_number integer NOT NULL,
  object_key varchar(500) NOT NULL UNIQUE,
  mime_type varchar(150) NOT NULL,
  size_bytes bigint NOT NULL,
  sha256 char(64) NOT NULL,
  status varchar(30) NOT NULL,
  embedding_model varchar(100),
  embedding_dimension integer,
  chunking_version varchar(30),
  error_code varchar(100),
  created_at timestamptz NOT NULL DEFAULT now(),
  activated_at timestamptz,
  UNIQUE (document_id, version_number)
);

ALTER TABLE knowledge.knowledge_document
  ADD CONSTRAINT fk_knowledge_active_version
  FOREIGN KEY (active_version_id) REFERENCES knowledge.document_version(id);

CREATE TABLE knowledge.document_acl (
  document_id uuid NOT NULL REFERENCES knowledge.knowledge_document(id) ON DELETE CASCADE,
  principal_type varchar(20) NOT NULL CHECK (principal_type IN ('USER','ROLE')),
  principal_value varchar(100) NOT NULL,
  PRIMARY KEY (document_id, principal_type, principal_value)
);

-- Dimension 1536 is an initial configuration. A model change requires a new migration/table strategy.
CREATE TABLE knowledge.document_chunk (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  document_id uuid NOT NULL REFERENCES knowledge.knowledge_document(id),
  document_version_id uuid NOT NULL REFERENCES knowledge.document_version(id),
  ordinal integer NOT NULL,
  page_number integer,
  heading varchar(500),
  content text NOT NULL,
  content_checksum char(64) NOT NULL,
  search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
  embedding vector(1536) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (document_version_id, ordinal)
);
CREATE INDEX ix_chunk_vector ON knowledge.document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ix_chunk_text ON knowledge.document_chunk USING gin (search_vector);
CREATE INDEX ix_chunk_tenant_doc ON knowledge.document_chunk(tenant_id, document_id);

-- Copy this generic table into each service-owned database/schema.
CREATE TABLE ticket.outbox_event (
  id uuid PRIMARY KEY,
  aggregate_type varchar(80) NOT NULL,
  aggregate_id uuid NOT NULL,
  event_type varchar(120) NOT NULL,
  payload jsonb NOT NULL,
  occurred_at timestamptz NOT NULL,
  published_at timestamptz,
  attempt_count integer NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_pending ON ticket.outbox_event(next_attempt_at) WHERE published_at IS NULL;

CREATE TABLE ticket.processed_event (
  consumer_name varchar(100) NOT NULL,
  event_id uuid NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer_name, event_id)
);

