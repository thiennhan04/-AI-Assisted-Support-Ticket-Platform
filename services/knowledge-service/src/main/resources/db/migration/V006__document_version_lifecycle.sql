ALTER TABLE knowledge.document_version
    DROP CONSTRAINT document_version_status_check,
    ADD CONSTRAINT document_version_status_check CHECK (
        status IN ('UPLOADING', 'QUEUED', 'PROCESSING', 'ACTIVE', 'FAILED', 'SUPERSEDED', 'DELETING', 'DELETED')),
    ADD COLUMN file_name varchar(255),
    ADD COLUMN cleanup_after timestamptz,
    ADD COLUMN cleanup_token uuid,
    ADD COLUMN cleanup_lease_until timestamptz,
    ADD COLUMN cleanup_attempts integer NOT NULL DEFAULT 0 CHECK (cleanup_attempts >= 0),
    ADD COLUMN cleanup_error_code varchar(100),
    ADD COLUMN cleaned_at timestamptz;

UPDATE knowledge.document_version v SET file_name = d.file_name
FROM knowledge.knowledge_document d WHERE d.id = v.document_id;
ALTER TABLE knowledge.document_version ALTER COLUMN file_name SET NOT NULL;

CREATE INDEX idx_document_version_cleanup ON knowledge.document_version(cleanup_after)
    WHERE cleanup_after IS NOT NULL AND cleaned_at IS NULL;

CREATE UNIQUE INDEX idx_one_activation_per_document_version
    ON knowledge.outbox_event ((payload->'data'->>'documentVersionId'))
    WHERE event_type = 'knowledge.document.activated.v1';
