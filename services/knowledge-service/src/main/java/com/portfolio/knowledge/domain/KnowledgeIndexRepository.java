package com.portfolio.knowledge.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface KnowledgeIndexRepository {
    List<IndexChunk> loadPrepared(UUID tenantId, UUID documentId, UUID versionId, String modelId);

    void activate(
            UUID tenantId,
            UUID documentId,
            UUID versionId,
            String modelId,
            List<ChunkEmbedding> embeddings,
            Instant now);

    List<SearchMatch> search(
            UUID tenantId,
            UUID principalId,
            Set<String> roles,
            String modelId,
            float[] query,
            List<String> tags,
            int topK);

    record IndexChunk(UUID id, String content) {}

    record ChunkEmbedding(UUID chunkId, float[] vector) {}

    record SearchMatch(
            UUID chunkId,
            UUID documentId,
            String title,
            int version,
            Integer pageNumber,
            String heading,
            String excerpt,
            double score) {}
}
