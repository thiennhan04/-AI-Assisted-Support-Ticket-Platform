package com.portfolio.knowledge.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository.IndexChunk;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndexingServiceTest {
    private final KnowledgeIndexRepository index = mock(KnowledgeIndexRepository.class);
    private final EmbeddingPort embedding = mock(EmbeddingPort.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID document = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-18T00:00:00Z");
    private final IndexingService service =
            new IndexingService(index, embedding, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void batchesInOrderAndActivatesOnlyAfterEveryBatchSucceeds() {
        var chunks = preparedChunks(65);
        when(embedding.modelId()).thenReturn("test-model");
        when(index.loadPrepared(tenant, document, version, "test-model")).thenReturn(chunks);
        when(embedding.embed(anyList()))
                .thenAnswer(
                        invocation -> {
                            List<String> texts = invocation.getArgument(0);
                            var vectors = new ArrayList<float[]>();
                            for (var text : texts) {
                                vectors.add(vector());
                            }
                            return vectors;
                        });
        service.index(tenant, document, version);
        var order = inOrder(embedding, index);
        order.verify(embedding).modelId();
        order.verify(index).loadPrepared(tenant, document, version, "test-model");
        order.verify(embedding).embed(argThat(texts -> texts.size() == 64));
        order.verify(embedding).embed(List.of("chunk-64"));
        order.verify(index)
                .activate(
                        eq(tenant),
                        eq(document),
                        eq(version),
                        eq("test-model"),
                        argThat(
                                results ->
                                        results.size() == 65
                                                && results.get(64)
                                                        .chunkId()
                                                        .equals(chunks.get(64).id())),
                        eq(now));
    }

    @Test
    void secondBatchFailureDoesNotPersistAnyPartialVectorsAndCanRetry() {
        when(embedding.modelId()).thenReturn("test-model");
        when(index.loadPrepared(tenant, document, version, "test-model"))
                .thenReturn(preparedChunks(65));
        when(embedding.embed(anyList()))
                .thenAnswer(
                        invocation -> {
                            List<String> texts = invocation.getArgument(0);
                            if (texts.size() == 1) {
                                throw new DocumentException(
                                        "EMBEDDING_UNAVAILABLE", 503, "Unavailable");
                            }
                            var vectors = new ArrayList<float[]>();
                            for (var text : texts) {
                                vectors.add(vector());
                            }
                            return vectors;
                        });
        assertThatThrownBy(() -> service.index(tenant, document, version))
                .isInstanceOf(DocumentException.class);
        verify(index, never()).activate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWrongDimensionNonFiniteZeroAndWrongCountBeforeActivation() {
        when(embedding.modelId()).thenReturn("test-model");
        when(index.loadPrepared(tenant, document, version, "test-model"))
                .thenReturn(preparedChunks(1));
        var nan = vector();
        nan[1] = Float.NaN;
        for (var invalid : List.of(new float[3], new float[EmbeddingPort.DIMENSIONS], nan)) {
            doReturn(List.of(invalid)).when(embedding).embed(anyList());
            assertThatThrownBy(() -> service.index(tenant, document, version))
                    .isInstanceOf(DocumentException.class);
        }
        doReturn(List.of()).when(embedding).embed(anyList());
        assertThatThrownBy(() -> service.index(tenant, document, version))
                .isInstanceOf(DocumentException.class);
        verify(index, never()).activate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void alreadyActiveVersionSkipsProviderAndActivation() {
        when(embedding.modelId()).thenReturn("test-model");
        when(index.loadPrepared(tenant, document, version, "test-model")).thenReturn(List.of());
        service.index(tenant, document, version);
        verify(embedding, never()).embed(anyList());
        verify(index, never()).activate(any(), any(), any(), any(), any(), any());
    }

    private List<IndexChunk> preparedChunks(int count) {
        var chunks = new ArrayList<IndexChunk>();
        for (int i = 0; i < count; i++) {
            chunks.add(new IndexChunk(UUID.randomUUID(), "chunk-" + i));
        }
        return chunks;
    }

    private float[] vector() {
        var vector = new float[EmbeddingPort.DIMENSIONS];
        vector[0] = 1;
        return vector;
    }
}
