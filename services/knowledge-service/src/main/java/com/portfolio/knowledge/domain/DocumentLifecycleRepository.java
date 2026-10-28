package com.portfolio.knowledge.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DocumentLifecycleRepository {
    KnowledgeDocument stageReplacement(
            UUID tenantId,
            UUID documentId,
            int expectedVersion,
            DocumentVersion version,
            String fileName,
            Instant now);

    void requestDeletion(UUID tenantId, UUID documentId, Instant now);

    Optional<CleanupWork> claimCleanup(Instant now);

    void completeCleanup(CleanupWork work, Instant now);

    void retryCleanup(CleanupWork work, Instant now);

    record CleanupWork(
            UUID tenantId, UUID documentId, UUID versionId, String objectKey, UUID token) {}
}
