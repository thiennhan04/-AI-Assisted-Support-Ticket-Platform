package com.portfolio.knowledge.application;

import com.portfolio.knowledge.api.dto.CreateDocumentRequest;
import com.portfolio.knowledge.domain.DocumentAccessScope;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentRepository;
import com.portfolio.knowledge.domain.DocumentStatus;
import com.portfolio.knowledge.domain.DocumentVersion;
import com.portfolio.knowledge.domain.KnowledgeDocument;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    public static final long MAX_SIZE_BYTES = 25L * 1024 * 1024;
    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final Clock clock;

    public DocumentService(DocumentRepository documents, DocumentStorage storage, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public KnowledgeDocument upload(
            AuthenticatedPrincipal caller, CreateDocumentRequest request, MultipartFile file) {
        requireAdmin(caller);
        if (file == null || file.isEmpty()) {
            throw new DocumentException("INVALID_UPLOAD", 400, "A non-empty file is required");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new DocumentException(
                    "UPLOAD_TOO_LARGE", 413, "File size must not exceed 25 MiB");
        }
        var fileName = sanitizeFileName(file.getOriginalFilename());
        var documentId = UUID.randomUUID();
        var versionId = UUID.randomUUID();
        var objectKey = "documents/" + caller.tenantId() + "/" + versionId;

        // Storage validates actual bytes before writing; client MIME headers are not trusted.
        var stored = storage.upload(objectKey, file);
        var now = clock.instant();
        var version =
                new DocumentVersion(
                        versionId,
                        1,
                        objectKey,
                        stored.mimeType(),
                        stored.sizeBytes(),
                        stored.sha256(),
                        DocumentStatus.QUEUED,
                        null);
        var document =
                new KnowledgeDocument(
                        documentId,
                        caller.tenantId(),
                        request.title().strip(),
                        fileName,
                        request.visibility() == null
                                ? DocumentAccessScope.SUPPORT_TEAM_ONLY
                                : request.visibility(),
                        request.tags() == null ? List.of() : request.tags(),
                        caller.userId(),
                        now,
                        now,
                        version);
        documents.save(document);
        documents.appendIngestionRequested(document, now);
        return document;
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument get(AuthenticatedPrincipal caller, UUID id) {
        requireAdmin(caller);
        return documents.find(caller.tenantId(), id).orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list(AuthenticatedPrincipal caller, int page, int size) {
        requireAdmin(caller);
        return documents.list(caller.tenantId(), page, size);
    }

    private void requireAdmin(AuthenticatedPrincipal caller) {
        if (!caller.hasRole(Roles.ADMIN)) {
            throw new DocumentException(
                    "DOCUMENT_FORBIDDEN", 403, "Administrator role is required");
        }
    }

    private DocumentException notFound() {
        return new DocumentException("DOCUMENT_NOT_FOUND", 404, "Document was not found");
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            throw new DocumentException("INVALID_FILE_NAME", 400, "File name is required");
        }
        var name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isBlank() || name.length() > 255) {
            throw new DocumentException(
                    "INVALID_FILE_NAME", 400, "File name must contain 1 to 255 characters");
        }
        return name;
    }
}
