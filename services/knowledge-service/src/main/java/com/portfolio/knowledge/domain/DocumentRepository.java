package com.portfolio.knowledge.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {
    KnowledgeDocument save(KnowledgeDocument document);

    Optional<KnowledgeDocument> find(UUID tenantId, UUID id);

    List<KnowledgeDocument> list(UUID tenantId, int page, int size);

    void appendIngestionRequested(KnowledgeDocument document, Instant now);
}
