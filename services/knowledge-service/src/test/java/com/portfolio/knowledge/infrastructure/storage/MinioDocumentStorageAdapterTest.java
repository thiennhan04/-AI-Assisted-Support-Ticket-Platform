package com.portfolio.knowledge.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.portfolio.knowledge.domain.DocumentException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MinioDocumentStorageAdapterTest {
    @Test
    void computesMetadataFromActualBytesInsteadOfClientMimeHeader() {
        var client = mock(MinioClient.class);
        var storage = new MinioDocumentStorageAdapter(client, "knowledge");
        var file =
                new MockMultipartFile(
                        "file",
                        "guide.pdf",
                        "application/pdf",
                        "guide".getBytes(StandardCharsets.UTF_8));
        var stored = storage.upload("documents/test", file);
        assertThat(stored.mimeType()).isEqualTo("text/plain");
        assertThat(stored.sizeBytes()).isEqualTo(5);
        assertThat(stored.sha256()).hasSize(64);
    }

    @Test
    void rejectsUnsupportedContentBeforeCallingMinio() {
        var client = mock(MinioClient.class);
        var storage = new MinioDocumentStorageAdapter(client, "knowledge");
        var file =
                new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[] {0, 1, 2});
        assertThatThrownBy(() -> storage.upload("documents/test", file))
                .isInstanceOf(DocumentException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_DOCUMENT_TYPE");
        verifyNoInteractions(client);
    }

    @Test
    void wrapsMinioFailureWithoutExposingProviderDetails() throws Exception {
        var client = mock(MinioClient.class);
        when(client.putObject(any(PutObjectArgs.class)))
                .thenThrow(new java.io.IOException("Provider failure"));
        var storage = new MinioDocumentStorageAdapter(client, "knowledge");
        var file =
                new MockMultipartFile(
                        "file",
                        "guide.txt",
                        "text/plain",
                        "guide".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> storage.upload("documents/test", file))
                .isInstanceOf(DocumentException.class)
                .hasMessage("Object storage is unavailable; retry later")
                .extracting("code")
                .isEqualTo("STORAGE_UNAVAILABLE");
    }

    @Test
    void detectsPdfAndUtf8TextByContentRatherThanFileName() {
        assertThat(
                        MinioDocumentStorageAdapter.detectMimeType(
                                "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("application/pdf");
        assertThat(
                        MinioDocumentStorageAdapter.detectMimeType(
                                "Account guide\n".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("text/plain");
        assertThat(MinioDocumentStorageAdapter.detectMimeType(new byte[] {0, 1, 2}))
                .isEqualTo("application/octet-stream");
        assertThat(MinioDocumentStorageAdapter.detectMimeType(new byte[] {(byte) 0xff}))
                .isEqualTo("application/octet-stream");
    }

    @Test
    void distinguishesDocxFromAnArbitraryZip() throws Exception {
        var docx = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(docx)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(
                    "<Types>application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml</Types>"
                            .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        assertThat(MinioDocumentStorageAdapter.detectMimeType(docx.toByteArray()))
                .isEqualTo(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(MinioDocumentStorageAdapter.detectMimeType(new byte[] {'P', 'K', 3, 4}))
                .isEqualTo("application/octet-stream");
    }
}
