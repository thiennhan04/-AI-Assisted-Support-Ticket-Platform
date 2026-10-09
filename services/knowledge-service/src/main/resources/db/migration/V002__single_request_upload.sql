-- Retire reservations from the old signed-upload flow without deleting their metadata.
UPDATE knowledge.document_version
SET status = 'FAILED', error_code = 'UPLOAD_FLOW_REPLACED'
WHERE status = 'UPLOADING';

UPDATE knowledge.knowledge_document d
SET status = v.status, updated_at = now()
FROM knowledge.document_version v
WHERE d.current_version_id = v.id AND v.error_code = 'UPLOAD_FLOW_REPLACED';

DROP INDEX IF EXISTS knowledge.idx_document_upload_expiry;
ALTER TABLE knowledge.document_version DROP COLUMN upload_expires_at;
