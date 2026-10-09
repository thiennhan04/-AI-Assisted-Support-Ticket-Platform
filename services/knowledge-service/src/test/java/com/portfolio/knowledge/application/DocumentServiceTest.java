package com.portfolio.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.portfolio.knowledge.api.dto.CreateDocumentRequest;
import com.portfolio.knowledge.domain.DocumentAccessScope;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.DocumentRepository;
import com.portfolio.knowledge.domain.DocumentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final AuthenticatedPrincipal admin =
            new AuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), Set.of("ADMIN"));
    private final DocumentService service =
            new DocumentService(documents, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    private final CreateDocumentRequest request = new CreateDocumentRequest(" Guide ", null, null);
    private final MockMultipartFile file =
            new MockMultipartFile(
                    "file",
                    "C:\\fakepath\\guide.txt",
                    "application/pdf",
                    "guide".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Test
    void uploadsVerifiedFileAndSavesQueuedDocumentWithOneIngestionRequest() {
        when(storage.upload(anyString(), eq(file)))
                .thenReturn(new DocumentStorage.StoredFile(5, "text/plain", "a".repeat(64)));
        var document = service.upload(admin, request, file);
        assertThat(document.tenantId()).isEqualTo(admin.tenantId());
        assertThat(document.fileName()).isEqualTo("guide.txt");
        assertThat(document.title()).isEqualTo("Guide");
        assertThat(document.visibility()).isEqualTo(DocumentAccessScope.SUPPORT_TEAM_ONLY);
        assertThat(document.tags()).isEmpty();
        assertThat(document.version().status()).isEqualTo(DocumentStatus.QUEUED);
        assertThat(document.version().mimeType()).isEqualTo("text/plain");
        assertThat(document.version().sha256()).isEqualTo("a".repeat(64));
        assertThat(document.version().objectKey())
                .startsWith("documents/" + admin.tenantId() + "/");
        var order = inOrder(storage, documents);
        order.verify(storage).upload(anyString(), eq(file));
        order.verify(documents).save(document);
        order.verify(documents).appendIngestionRequested(document, NOW);
    }

    @Test
    void rejectsNonAdminBeforeUploadingFile() {
        var customer =
                new AuthenticatedPrincipal(admin.userId(), admin.tenantId(), Set.of("CUSTOMER"));
        assertThatThrownBy(() -> service.upload(customer, request, file))
                .isInstanceOf(DocumentException.class)
                .extracting("status")
                .isEqualTo(403);
        verifyNoInteractions(storage, documents);
    }

    @Test
    void rejectsMissingOrEmptyFile() {
        assertThatThrownBy(() -> service.upload(admin, request, null))
                .isInstanceOf(DocumentException.class)
                .extracting("code")
                .isEqualTo("INVALID_UPLOAD");
        var empty = new MockMultipartFile("file", "guide.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.upload(admin, request, empty))
                .isInstanceOf(DocumentException.class)
                .extracting("code")
                .isEqualTo("INVALID_UPLOAD");
        verifyNoInteractions(storage, documents);
    }

    @Test
    void rejectsOversizedFileBeforeCallingStorage() {
        var oversized =
                new MockMultipartFile("file", "guide.txt", "text/plain", new byte[] {1}) {
                    @Override
                    public long getSize() {
                        return DocumentService.MAX_SIZE_BYTES + 1;
                    }
                };
        assertThatThrownBy(() -> service.upload(admin, request, oversized))
                .isInstanceOf(DocumentException.class)
                .extracting("status")
                .isEqualTo(413);
        verifyNoInteractions(storage, documents);
    }

    @Test
    void rejectsInvalidFileNameBeforeCallingStorage() {
        var unnamed = new MockMultipartFile("file", "../", "text/plain", new byte[] {1});
        assertThatThrownBy(() -> service.upload(admin, request, unnamed))
                .isInstanceOf(DocumentException.class)
                .extracting("code")
                .isEqualTo("INVALID_FILE_NAME");
        verifyNoInteractions(storage, documents);
    }

    @Test
    void storageFailureDoesNotCreateMetadataOrEvent() {
        when(storage.upload(anyString(), eq(file)))
                .thenThrow(new DocumentException("STORAGE_UNAVAILABLE", 503, "Retry later"));
        assertThatThrownBy(() -> service.upload(admin, request, file))
                .isInstanceOf(DocumentException.class);
        verifyNoInteractions(documents);
    }

    @Test
    void keepsExplicitVisibilityAndTags() {
        when(storage.upload(anyString(), eq(file)))
                .thenReturn(new DocumentStorage.StoredFile(5, "text/plain", "a".repeat(64)));
        var document =
                service.upload(
                        admin,
                        new CreateDocumentRequest(
                                "Guide", DocumentAccessScope.ALL_TENANT_USERS, List.of("account")),
                        file);
        assertThat(document.visibility()).isEqualTo(DocumentAccessScope.ALL_TENANT_USERS);
        assertThat(document.tags()).containsExactly("account");
    }

    @Test
    void hidesOtherTenantsDocumentsAndDeniesNonAdminRead() {
        when(documents.find(
                        admin.tenantId(), UUID.fromString("00000000-0000-4000-8000-000000000001")))
                .thenReturn(Optional.empty());
        assertThatThrownBy(
                        () ->
                                service.get(
                                        admin,
                                        UUID.fromString("00000000-0000-4000-8000-000000000001")))
                .isInstanceOf(DocumentException.class)
                .extracting("status")
                .isEqualTo(404);
        var customer =
                new AuthenticatedPrincipal(admin.userId(), admin.tenantId(), Set.of("CUSTOMER"));
        assertThatThrownBy(() -> service.list(customer, 0, 20))
                .isInstanceOf(DocumentException.class)
                .extracting("status")
                .isEqualTo(403);
    }
}
