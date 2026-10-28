package com.portfolio.knowledge.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.portfolio.knowledge.domain.DocumentAccessScope;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentRepository;
import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.DocumentVersion;
import com.portfolio.knowledge.domain.KnowledgeDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngestionServiceTest {
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final TextExtractor extractor = mock(TextExtractor.class);
    private final Instant now = Instant.parse("2026-10-10T00:00:00Z");
    private final IngestionService service =
            new IngestionService(
                    documents,
                    storage,
                    extractor,
                    new DocumentChunker(),
                    Clock.fixed(now, ZoneOffset.UTC));
    private final byte[] bytes = "Account guide".getBytes(StandardCharsets.UTF_8);
    private KnowledgeDocument document;

    @BeforeEach
    void setup() throws Exception {
        var version =
                new DocumentVersion(
                        UUID.randomUUID(),
                        1,
                        "documents/tenant/version",
                        "text/plain",
                        bytes.length,
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                        DocumentStatus.QUEUED,
                        null);
        document =
                new KnowledgeDocument(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Guide",
                        "guide.txt",
                        DocumentAccessScope.SUPPORT_TEAM_ONLY,
                        List.of(),
                        UUID.randomUUID(),
                        now,
                        now,
                        version,
                        null,
                        DocumentStatus.QUEUED);
        when(documents.find(document.tenantId(), document.id())).thenReturn(Optional.of(document));
        when(storage.download(version.objectKey())).thenReturn(bytes);
        when(extractor.extract(bytes, "text/plain"))
                .thenReturn(List.of(new TextExtractor.Section(null, "Account", "Account guide")));
    }

    @Test
    void verifiesStoredBytesBeforeExtractingAndSavingChunks() {
        prepare();
        var order = inOrder(storage, extractor, documents);
        order.verify(storage).download(document.version().objectKey());
        order.verify(extractor).extract(bytes, "text/plain");
        order.verify(documents)
                .saveChunks(
                        eq(document.tenantId()),
                        eq(document.id()),
                        eq(document.version().id()),
                        argThat(
                                chunks ->
                                        chunks.size() == 1
                                                && chunks.getFirst().heading().equals("Account")),
                        eq(DocumentChunker.CHUNKING_VERSION),
                        eq(now));
    }

    @Test
    void duplicatePreparedVersionDoesNotReadStorageOrCreateChunks() {
        when(documents.isPrepared(document.tenantId(), document.id(), document.version().id()))
                .thenReturn(true);
        prepare();
        verifyNoInteractions(storage, extractor);
        verify(documents, never()).saveChunks(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsWrongTenantOrStaleVersionBeforeStorageAccess() {
        assertThatThrownBy(
                        () ->
                                service.prepare(
                                        UUID.randomUUID(), document.id(), document.version().id()))
                .isInstanceOf(DocumentException.class);
        assertThatThrownBy(
                        () ->
                                service.prepare(
                                        document.tenantId(), document.id(), UUID.randomUUID()))
                .isInstanceOfSatisfying(
                        DocumentException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.code())
                                        .isEqualTo("DOCUMENT_VERSION_CONFLICT"));
        verifyNoInteractions(storage, extractor);
    }

    @Test
    void mismatchedChecksumMarksFailureWithoutExtractingOrSavingChunks() {
        when(storage.download(document.version().objectKey()))
                .thenReturn("Changed bytes".getBytes(StandardCharsets.UTF_8));
        assertFailure("DOCUMENT_CONTENT_MISMATCH");
        verifyNoInteractions(extractor);
        verify(documents)
                .failPreparation(
                        document.tenantId(),
                        document.id(),
                        document.version().id(),
                        "DOCUMENT_CONTENT_MISMATCH",
                        now);
        verify(documents, never()).saveChunks(any(), any(), any(), any(), any(), any());
    }

    @Test
    void emptyOrEncryptedContentMarksPermanentFailure() {
        for (var code : List.of("DOCUMENT_EMPTY", "DOCUMENT_ENCRYPTED", "DOCUMENT_INVALID")) {
            doThrow(new DocumentException(code, 422, "Cannot extract document"))
                    .when(extractor)
                    .extract(bytes, "text/plain");
            assertFailure(code);
            verify(documents)
                    .failPreparation(
                            document.tenantId(), document.id(), document.version().id(), code, now);
        }
        verify(documents, never()).saveChunks(any(), any(), any(), any(), any(), any());
    }

    @Test
    void storageOutageLeavesVersionQueuedForRetry() {
        when(storage.download(document.version().objectKey()))
                .thenThrow(
                        new DocumentException("STORAGE_UNAVAILABLE", 503, "Storage unavailable"));
        assertFailure("STORAGE_UNAVAILABLE");
        verify(documents, never()).failPreparation(any(), any(), any(), any(), any());
        verifyNoInteractions(extractor);
    }

    @Test
    void databaseFailureDoesNotMarkDocumentAsInvalid() {
        doThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "Database unavailable"))
                .when(documents)
                .saveChunks(any(), any(), any(), any(), any(), any());
        assertThatThrownBy(this::prepare)
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(documents, never()).failPreparation(any(), any(), any(), any(), any());
    }

    private void prepare() {
        service.prepare(document.tenantId(), document.id(), document.version().id());
    }

    private void assertFailure(String code) {
        assertThatThrownBy(this::prepare)
                .isInstanceOfSatisfying(
                        DocumentException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.code())
                                        .isEqualTo(code));
    }
}
