ALTER TABLE knowledge.document_version
    ADD COLUMN chunking_version varchar(30),
    ADD COLUMN prepared_at timestamptz;

ALTER TABLE knowledge.knowledge_document ADD UNIQUE (tenant_id, id);

CREATE TABLE knowledge.document_chunk (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    document_id uuid NOT NULL,
    document_version_id uuid NOT NULL,
    ordinal integer NOT NULL CHECK (ordinal >= 0),
    page_number integer CHECK (page_number > 0),
    heading varchar(500),
    content text NOT NULL CHECK (length(content) > 0),
    content_checksum char(64) NOT NULL CHECK (content_checksum ~ '^[a-f0-9]{64}$'),
    token_count integer NOT NULL CHECK (token_count BETWEEN 1 AND 1000),
    created_at timestamptz NOT NULL,
    UNIQUE (document_version_id, ordinal),
    FOREIGN KEY (tenant_id, document_id) REFERENCES knowledge.knowledge_document(tenant_id, id),
    FOREIGN KEY (document_id, document_version_id) REFERENCES knowledge.document_version(document_id, id)
);

CREATE INDEX idx_chunk_tenant_version ON knowledge.document_chunk(tenant_id, document_version_id);
