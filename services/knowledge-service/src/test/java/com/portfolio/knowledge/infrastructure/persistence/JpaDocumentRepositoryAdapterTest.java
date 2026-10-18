package com.portfolio.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.knowledge.application.DocumentChunker;
import com.portfolio.knowledge.domain.Chunk;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JpaDocumentRepositoryAdapterTest {
    private final DocumentSpringDataRepository documents = mock(DocumentSpringDataRepository.class);
    private final ChunkSpringDataRepository chunks = mock(ChunkSpringDataRepository.class);
    private final JpaDocumentRepositoryAdapter adapter =
            new JpaDocumentRepositoryAdapter(
                    documents,
                    mock(KnowledgeOutboxSpringDataRepository.class),
                    new ObjectMapper(),
                    chunks);
    private final Instant now = Instant.parse("2026-10-10T00:00:00Z");
    private final List<Chunk> prepared =
            List.of(new Chunk(0, 1, "Guide", "Account guide", "a".repeat(64), 2));

    @Test
    void storesChunksAndPreparedMarkerUnderDocumentLock() {
        var document = queuedDocument();
        save(document);
        var order = inOrder(documents, chunks);
        order.verify(documents).findForPreparation(document.tenantId, document.id);
        order.verify(chunks)
                .saveAll(
                        argThat(
                                entities -> {
                                    var entity = entities.iterator().next();
                                    return entity.tenantId.equals(document.tenantId)
                                            && entity.documentVersionId.equals(document.version.id)
                                            && entity.content.equals("Account guide");
                                }));
        order.verify(documents).saveAndFlush(document);
        assertThat(document.status).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.version.status).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.version.preparedAt).isEqualTo(now);
        assertThat(document.version.chunkingVersion).isEqualTo(DocumentChunker.CHUNKING_VERSION);
    }

    @Test
    void concurrentWinnerMarkerMakesSecondSaveANoOp() {
        var document = queuedDocument();
        document.version.preparedAt = now;
        document.status = DocumentStatus.PROCESSING;
        save(document);
        verifyNoInteractions(chunks);
        verify(documents, never()).saveAndFlush(any());
    }

    @Test
    void staleVersionCannotWriteChunks() {
        var document = queuedDocument();
        assertThatThrownBy(
                        () ->
                                adapter.saveChunks(
                                        document.tenantId,
                                        document.id,
                                        UUID.randomUUID(),
                                        prepared,
                                        DocumentChunker.CHUNKING_VERSION,
                                        now))
                .isInstanceOf(DocumentException.class);
        verifyNoInteractions(chunks);
    }

    @Test
    void permanentFailureUpdatesBothStatusesWithoutWritingChunks() {
        var document = queuedDocument();
        adapter.failPreparation(
                document.tenantId, document.id, document.version.id, "DOCUMENT_EMPTY", now);
        assertThat(document.status).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.version.status).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.version.errorCode).isEqualTo("DOCUMENT_EMPTY");
        assertThat(document.version.preparedAt).isNull();
        verifyNoInteractions(chunks);
    }

    @Test
    void lateFailureCannotOverwriteSuccessfullyPreparedVersion() {
        var document = queuedDocument();
        document.version.preparedAt = now;
        document.version.status = DocumentStatus.PROCESSING;
        document.status = DocumentStatus.PROCESSING;
        adapter.failPreparation(
                document.tenantId, document.id, document.version.id, "DOCUMENT_INVALID", now);
        assertThat(document.status).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(document.version.errorCode).isNull();
        verify(documents, never()).saveAndFlush(any());
    }

    private DocumentJpaEntity queuedDocument() {
        var document = new DocumentJpaEntity();
        document.id = UUID.randomUUID();
        document.tenantId = UUID.randomUUID();
        document.status = DocumentStatus.QUEUED;
        document.version = new DocumentVersionJpaEntity();
        document.version.id = UUID.randomUUID();
        document.version.status = DocumentStatus.QUEUED;
        when(documents.findForPreparation(document.tenantId, document.id))
                .thenReturn(Optional.of(document));
        return document;
    }

    private void save(DocumentJpaEntity document) {
        adapter.saveChunks(
                document.tenantId,
                document.id,
                document.version.id,
                prepared,
                DocumentChunker.CHUNKING_VERSION,
                now);
    }
}
