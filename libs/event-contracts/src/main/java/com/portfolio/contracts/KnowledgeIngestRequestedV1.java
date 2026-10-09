package com.portfolio.contracts;

import java.util.UUID;

public record KnowledgeIngestRequestedV1(UUID documentId, UUID documentVersionId) {
    public KnowledgeIngestRequestedV1 {
        java.util.Objects.requireNonNull(documentId);
        java.util.Objects.requireNonNull(documentVersionId);
    }
}
