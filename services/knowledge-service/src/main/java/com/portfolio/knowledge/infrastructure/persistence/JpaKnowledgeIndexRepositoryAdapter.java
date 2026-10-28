package com.portfolio.knowledge.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.contracts.KnowledgeDocumentActivatedV1;
import com.portfolio.knowledge.application.DocumentCleanupProperties;
import com.portfolio.knowledge.application.EmbeddingPort;
import com.portfolio.knowledge.application.Roles;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaKnowledgeIndexRepositoryAdapter implements KnowledgeIndexRepository {
    private final DocumentSpringDataRepository documents;
    private final ChunkSpringDataRepository chunks;
    private final ObjectMapper mapper;
    private final DocumentVersionSpringDataRepository versions;
    private final KnowledgeOutboxSpringDataRepository outbox;
    private final DocumentCleanupProperties cleanup;

    public JpaKnowledgeIndexRepositoryAdapter(
            DocumentSpringDataRepository documents,
            ChunkSpringDataRepository chunks,
            ObjectMapper mapper,
            DocumentVersionSpringDataRepository versions,
            KnowledgeOutboxSpringDataRepository outbox,
            DocumentCleanupProperties cleanup) {
        this.documents = documents;
        this.chunks = chunks;
        this.mapper = mapper;
        this.versions = versions;
        this.outbox = outbox;
        this.cleanup = cleanup;
    }

    @Override
    @Transactional(readOnly = true)
    public List<IndexChunk> loadPrepared(
            UUID tenantId, UUID documentId, UUID versionId, String modelId) {
        var document =
                documents.findByTenantIdAndId(tenantId, documentId).orElseThrow(this::notFound);
        checkVersion(document, versionId);
        if (alreadyActive(document, modelId)) {
            return List.of();
        }
        requirePrepared(document);
        var entities =
                chunks.findByTenantIdAndDocumentIdAndDocumentVersionIdOrderByOrdinal(
                        tenantId, documentId, versionId);
        if (entities.isEmpty()) {
            throw new DocumentException("DOCUMENT_EMPTY", 409, "Prepared document has no chunks");
        }
        var results = new ArrayList<IndexChunk>();
        for (var entity : entities) {
            results.add(new IndexChunk(entity.id, entity.content));
        }
        return List.copyOf(results);
    }

    @Override
    @Transactional
    public void activate(
            UUID tenantId,
            UUID documentId,
            UUID versionId,
            String modelId,
            List<ChunkEmbedding> embeddings,
            Instant now) {
        var document =
                documents.findForPreparation(tenantId, documentId).orElseThrow(this::notFound);
        checkVersion(document, versionId);
        if (alreadyActive(document, modelId)) {
            return;
        }
        requirePrepared(document);
        if (modelId == null || modelId.isBlank() || modelId.length() > 100) {
            throw EmbeddingPort.invalidResponse();
        }
        var entities =
                chunks.findByTenantIdAndDocumentIdAndDocumentVersionIdOrderByOrdinal(
                        tenantId, documentId, versionId);
        if (entities.isEmpty() || entities.size() != embeddings.size()) {
            throw EmbeddingPort.invalidResponse();
        }
        var byId = new HashMap<UUID, float[]>();
        for (var embedding : embeddings) {
            EmbeddingPort.validate(embedding.vector());
            if (byId.put(embedding.chunkId(), embedding.vector()) != null) {
                throw EmbeddingPort.invalidResponse();
            }
        }
        for (var entity : entities) {
            var vector = byId.get(entity.id);
            EmbeddingPort.validate(vector);
            entity.embedding = vector.clone();
        }
        chunks.saveAll(entities);
        var previousVersionId = document.activeVersionId;
        if (previousVersionId != null && !previousVersionId.equals(versionId)) {
            var previous =
                    versions.findByDocumentIdAndId(documentId, previousVersionId)
                            .orElseThrow(this::notFound);
            previous.status = DocumentStatus.SUPERSEDED;
            previous.cleanupAfter = now.plus(cleanup.gracePeriod());
            versions.save(previous);
        }
        document.version.embeddingModel = modelId;
        document.version.embeddingDimension = EmbeddingPort.DIMENSIONS;
        document.version.activatedAt = now;
        document.version.status = DocumentStatus.ACTIVE;
        document.version.errorCode = null;
        document.activeVersionId = versionId;
        document.status = DocumentStatus.ACTIVE;
        document.updatedAt = now;
        documents.saveAndFlush(document);
        var data = new KnowledgeDocumentActivatedV1(documentId, versionId, previousVersionId);
        var event =
                KnowledgeOutboxJpaEntity.event(
                        KnowledgeDocumentActivatedV1.EVENT_TYPE,
                        tenantId,
                        documentId,
                        data,
                        now,
                        mapper);
        outbox.save(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchMatch> search(
            UUID tenantId,
            UUID principalId,
            Set<String> roles,
            String modelId,
            float[] query,
            List<String> tags,
            int topK) {
        EmbeddingPort.validate(query);
        var vectorText = new StringBuilder("[");
        for (int i = 0; i < query.length; i++) {
            if (i > 0) {
                vectorText.append(',');
            }
            vectorText.append(Float.toString(query[i]));
        }
        vectorText.append(']');
        String tagsJson;
        try {
            tagsJson = mapper.writeValueAsString(tags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode search tags", exception);
        }
        boolean staff = roles.contains(Roles.ADMIN) || roles.contains(Roles.AGENT);
        var rows =
                chunks.search(
                        tenantId,
                        principalId.toString(),
                        roles,
                        staff,
                        modelId,
                        vectorText.toString(),
                        tagsJson,
                        topK);
        var results = new ArrayList<SearchMatch>();
        for (var row : rows) {
            var match =
                    new SearchMatch(
                            row.getChunkId(),
                            row.getDocumentId(),
                            row.getTitle(),
                            row.getVersion(),
                            row.getPageNumber(),
                            row.getHeading(),
                            row.getExcerpt(),
                            row.getScore());
            results.add(match);
        }
        return List.copyOf(results);
    }

    private boolean alreadyActive(DocumentJpaEntity document, String modelId) {
        if (document.version.status != DocumentStatus.ACTIVE) {
            return false;
        }
        if (!modelId.equals(document.version.embeddingModel)) {
            throw new DocumentException(
                    "EMBEDDING_MODEL_CONFLICT",
                    409,
                    "Model changes require a new document version");
        }
        return document.version.id.equals(document.activeVersionId);
    }

    private void requirePrepared(DocumentJpaEntity document) {
        if (document.version.preparedAt == null
                || document.version.status != DocumentStatus.PROCESSING
                || document.status == DocumentStatus.DELETING
                || document.status == DocumentStatus.DELETED) {
            throw new DocumentException(
                    "DOCUMENT_NOT_PREPARED", 409, "Document is not ready for embedding");
        }
    }

    private void checkVersion(DocumentJpaEntity document, UUID versionId) {
        if (!document.version.id.equals(versionId)) {
            throw new DocumentException(
                    "DOCUMENT_VERSION_CONFLICT", 409, "Document version is no longer current");
        }
    }

    private DocumentException notFound() {
        return new DocumentException("DOCUMENT_NOT_FOUND", 404, "Document was not found");
    }
}
