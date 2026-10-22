-- The extension is provisioned by deploy/postgres for service-owned databases.
CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE knowledge.document_version
    ADD COLUMN embedding_model varchar(100),
    ADD COLUMN embedding_dimension integer CHECK (embedding_dimension = 1536),
    ADD COLUMN activated_at timestamptz;

ALTER TABLE knowledge.document_chunk ADD COLUMN embedding vector(1536);

CREATE TABLE knowledge.document_acl (
    document_id uuid NOT NULL REFERENCES knowledge.knowledge_document(id) ON DELETE CASCADE,
    principal_type varchar(20) NOT NULL CHECK (principal_type IN ('USER', 'ROLE')),
    principal_value varchar(100) NOT NULL,
    PRIMARY KEY (document_id, principal_type, principal_value)
);

CREATE INDEX idx_document_acl_lookup ON knowledge.document_acl(document_id, principal_type, principal_value);
-- Start with exact cosine ranking after access filtering. ANN indexing can be added with recall tests.
