package com.portfolio.knowledge.api.dto;

import com.portfolio.knowledge.domain.DocumentAccessScope;
import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DocumentResponse(
        UUID documentId,
        String title,
        String fileName,
        DocumentAccessScope visibility,
        List<String> tags,
        DocumentStatus status,
        UUID versionId,
        int versionNumber,
        String mimeType,
        long sizeBytes,
        String sha256,
        String errorCode,
        Instant createdAt,
        Instant updatedAt,
        UUID activeVersionId,
        DocumentStatus documentStatus) {

    public static DocumentResponse from(KnowledgeDocument document) {
        var version = document.version();
        return new DocumentResponse(
                document.id(),
                document.title(),
                document.fileName(),
                document.visibility(),
                document.tags(),
                version.status(),
                version.id(),
                version.versionNumber(),
                version.mimeType(),
                version.sizeBytes(),
                version.sha256(),
                version.errorCode(),
                document.createdAt(),
                document.updatedAt(),
                document.activeVersionId(),
                document.status());
    }
}
