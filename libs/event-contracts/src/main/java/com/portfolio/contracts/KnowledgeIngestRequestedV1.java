package com.portfolio.contracts;

import java.util.UUID;

public record KnowledgeIngestRequestedV1(UUID documentId, UUID documentVersionId) {
    public static final String EVENT_TYPE = "knowledge.ingest.requested.v1";

    public KnowledgeIngestRequestedV1 {
        java.util.Objects.requireNonNull(documentId);
        java.util.Objects.requireNonNull(documentVersionId);
    }
}
