package com.portfolio.contracts;

import java.util.Objects;
import java.util.UUID;

public record KnowledgeDocumentActivatedV1(
        UUID documentId, UUID documentVersionId, UUID previousVersionId) {
    public static final String EVENT_TYPE = "knowledge.document.activated.v1";

    public KnowledgeDocumentActivatedV1 {
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(documentVersionId);
    }
}
