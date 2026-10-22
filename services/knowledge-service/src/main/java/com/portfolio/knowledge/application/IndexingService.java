package com.portfolio.knowledge.application;

import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository.ChunkEmbedding;
import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexingService {
    private static final int BATCH_SIZE = 64;
    private final KnowledgeIndexRepository index;
    private final EmbeddingPort embedding;
    private final Clock clock;
    private final Semaphore jobs = new Semaphore(2);

    public IndexingService(KnowledgeIndexRepository index, EmbeddingPort embedding, Clock clock) {
        this.index = index;
        this.embedding = embedding;
        this.clock = clock;
    }

    // Called after IngestionService.prepare by the trusted ingestion workflow.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void index(UUID tenantId, UUID documentId, UUID versionId) {
        if (!jobs.tryAcquire()) {
            throw new DocumentException(
                    "INDEXING_BUSY", 503, "Too many documents are being indexed; retry later");
        }
        try {
            var modelId = embedding.modelId();
            var chunks = index.loadPrepared(tenantId, documentId, versionId, modelId);
            if (chunks.isEmpty()) {
                return;
            }
            var results = new ArrayList<ChunkEmbedding>();
            for (int start = 0; start < chunks.size(); start += BATCH_SIZE) {
                int end = Math.min(start + BATCH_SIZE, chunks.size());
                var batch = chunks.subList(start, end);
                var texts = new ArrayList<String>();
                for (var chunk : batch) {
                    texts.add(chunk.content());
                }
                var vectors = embedding.embed(texts);
                if (vectors == null || vectors.size() != batch.size()) {
                    throw EmbeddingPort.invalidResponse();
                }
                for (int i = 0; i < batch.size(); i++) {
                    var vector = vectors.get(i);
                    EmbeddingPort.validate(vector);
                    var result = new ChunkEmbedding(batch.get(i).id(), vector.clone());
                    results.add(result);
                }
            }
            // Commit only after every batch succeeds; provider failures leave the staged version
            // intact.
            index.activate(tenantId, documentId, versionId, modelId, results, clock.instant());
        } finally {
            jobs.release();
        }
    }
}
