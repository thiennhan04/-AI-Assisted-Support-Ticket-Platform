package com.portfolio.knowledge.application;

import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentRepository;
import com.portfolio.knowledge.domain.DocumentStatus;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService {
    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final TextExtractor extractor;
    private final DocumentChunker chunker;
    private final Clock clock;
    private final Semaphore preparations = new Semaphore(2);

    public IngestionService(
            DocumentRepository documents,
            DocumentStorage storage,
            TextExtractor extractor,
            DocumentChunker chunker,
            Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.extractor = extractor;
        this.chunker = chunker;
        this.clock = clock;
    }

    // Internal entry point for a trusted ingestion consumer; never accept tenant IDs from public
    // input.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void prepare(UUID tenantId, UUID documentId, UUID versionId) {
        var document =
                documents
                        .find(tenantId, documentId)
                        .orElseThrow(
                                () ->
                                        new DocumentException(
                                                "DOCUMENT_NOT_FOUND",
                                                404,
                                                "Document was not found"));
        var version = document.version();
        if (!version.id().equals(versionId)) {
            throw new DocumentException(
                    "DOCUMENT_VERSION_CONFLICT", 409, "Document version is no longer current");
        }
        if (document.status() == DocumentStatus.DELETING
                || document.status() == DocumentStatus.DELETED) {
            throw new DocumentException(
                    "DOCUMENT_DELETED", 409, "Deleted documents cannot be processed");
        }
        if (documents.isPrepared(tenantId, documentId, versionId)) {
            return;
        }
        if (version.status() != DocumentStatus.QUEUED) {
            throw new DocumentException(
                    "DOCUMENT_NOT_QUEUED", 409, "Document is not queued for extraction");
        }
        if (!preparations.tryAcquire()) {
            throw new DocumentException(
                    "INGESTION_BUSY", 503, "Too many documents are being processed; retry later");
        }
        try {
            // Object I/O and parsing happen outside the short database write transaction.
            var bytes = storage.download(version.objectKey());
            var checksum =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (bytes.length != version.sizeBytes() || !checksum.equals(version.sha256())) {
                throw new DocumentException(
                        "DOCUMENT_CONTENT_MISMATCH",
                        422,
                        "Stored document does not match its metadata");
            }
            var sections = extractor.extract(bytes, version.mimeType());
            var chunks = chunker.split(sections);
            documents.saveChunks(
                    tenantId,
                    documentId,
                    versionId,
                    chunks,
                    DocumentChunker.CHUNKING_VERSION,
                    clock.instant());
        } catch (DocumentException exception) {
            // Provider outages remain retryable; only permanent content errors mark the version
            // FAILED.
            if (exception.status() == 422) {
                documents.failPreparation(
                        tenantId, documentId, versionId, exception.code(), clock.instant());
            }
            throw exception;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } finally {
            preparations.release();
        }
    }
}
