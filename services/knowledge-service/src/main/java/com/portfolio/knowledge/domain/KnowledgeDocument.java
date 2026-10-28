package com.portfolio.knowledge.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeDocument(
        UUID id,
        UUID tenantId,
        String title,
        String fileName,
        DocumentAccessScope visibility,
        List<String> tags,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        DocumentVersion version,
        UUID activeVersionId,
        DocumentStatus status) {

    public KnowledgeDocument {
        tags = List.copyOf(tags);
    }
}
