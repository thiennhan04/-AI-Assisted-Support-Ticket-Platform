-- Keep existing documents while replacing the public access-scope names.
ALTER TABLE knowledge.knowledge_document
    DROP CONSTRAINT knowledge_document_visibility_check;

UPDATE knowledge.knowledge_document
SET visibility = CASE visibility
    WHEN 'TENANT' THEN 'ALL_TENANT_USERS'
    WHEN 'AGENTS_ONLY' THEN 'SUPPORT_TEAM_ONLY'
    WHEN 'RESTRICTED' THEN 'LIMITED_ACCESS'
    ELSE visibility
END;

ALTER TABLE knowledge.knowledge_document
    ADD CONSTRAINT knowledge_document_visibility_check
    CHECK (visibility IN ('ALL_TENANT_USERS', 'SUPPORT_TEAM_ONLY', 'LIMITED_ACCESS'));
