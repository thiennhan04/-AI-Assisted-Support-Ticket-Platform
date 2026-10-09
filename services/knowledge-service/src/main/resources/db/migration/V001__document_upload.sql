CREATE SCHEMA IF NOT EXISTS knowledge;

CREATE TABLE knowledge.knowledge_document (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    title varchar(300) NOT NULL,
    file_name varchar(255) NOT NULL,
    visibility varchar(30) NOT NULL CHECK (visibility IN ('TENANT', 'AGENTS_ONLY', 'RESTRICTED')),
    tags jsonb NOT NULL DEFAULT '[]'::jsonb,
    status varchar(30) NOT NULL,
    current_version_id uuid NOT NULL UNIQUE,
    active_version_id uuid,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE knowledge.document_version (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES knowledge.knowledge_document(id) DEFERRABLE INITIALLY DEFERRED,
    version_number integer NOT NULL CHECK (version_number > 0),
    object_key varchar(500) NOT NULL UNIQUE,
    mime_type varchar(150) NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes BETWEEN 1 AND 26214400),
    sha256 char(64),
    status varchar(30) NOT NULL CHECK (status IN ('UPLOADING', 'QUEUED', 'PROCESSING', 'ACTIVE', 'FAILED', 'DELETING', 'DELETED')),
    upload_expires_at timestamptz NOT NULL,
    error_code varchar(100),
    created_at timestamptz NOT NULL,
    UNIQUE (document_id, version_number),
    UNIQUE (document_id, id),
    CHECK (sha256 IS NULL OR sha256 ~ '^[a-f0-9]{64}$')
);

ALTER TABLE knowledge.knowledge_document
    ADD FOREIGN KEY (id, current_version_id) REFERENCES knowledge.document_version(document_id, id),
    ADD FOREIGN KEY (id, active_version_id) REFERENCES knowledge.document_version(document_id, id);

CREATE INDEX idx_document_tenant_created ON knowledge.knowledge_document(tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_document_upload_expiry ON knowledge.document_version(upload_expires_at) WHERE status = 'UPLOADING';

CREATE TABLE knowledge.outbox_event (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    subject_id uuid NOT NULL,
    event_type varchar(150) NOT NULL,
    routing_key varchar(150) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    published_at timestamptz
);
CREATE UNIQUE INDEX idx_one_ingestion_request_per_version
    ON knowledge.outbox_event ((payload->'data'->>'documentVersionId'))
    WHERE event_type = 'knowledge.ingest.requested.v1';
CREATE INDEX idx_knowledge_outbox_pending ON knowledge.outbox_event(next_attempt_at) WHERE status = 'PENDING';
