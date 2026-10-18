package com.portfolio.knowledge.infrastructure.storage;

import com.portfolio.knowledge.application.DocumentService;
import com.portfolio.knowledge.application.DocumentStorage;
import com.portfolio.knowledge.domain.DocumentException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MinioDocumentStorageAdapter implements DocumentStorage {
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final MinioClient client;
    private final String bucket;
    // Bound memory usage while inspecting files up to 25 MiB.
    private final Semaphore inspections = new Semaphore(2);

    public MinioDocumentStorageAdapter(
            MinioClient client, @Value("${knowledge.storage.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public byte[] download(String objectKey) {
        try (var stream =
                client.getObject(
                        GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
            var bytes = stream.readNBytes(Math.toIntExact(DocumentService.MAX_SIZE_BYTES + 1));
            if (bytes.length > DocumentService.MAX_SIZE_BYTES) {
                throw new DocumentException(
                        "UPLOAD_TOO_LARGE", 422, "Stored file exceeds the size limit");
            }
            return bytes;
        } catch (DocumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure(exception);
        }
    }

    @Override
    public StoredFile upload(String objectKey, MultipartFile file) {
        if (!inspections.tryAcquire()) {
            throw new DocumentException(
                    "STORAGE_BUSY", 503, "Too many uploads are being verified; retry later");
        }
        try (var stream = file.getInputStream()) {
            var bytes = stream.readNBytes(Math.toIntExact(DocumentService.MAX_SIZE_BYTES + 1));
            if (bytes.length == 0) {
                throw new DocumentException("INVALID_UPLOAD", 400, "A non-empty file is required");
            }
            if (bytes.length > DocumentService.MAX_SIZE_BYTES) {
                throw new DocumentException(
                        "UPLOAD_TOO_LARGE", 413, "File size must not exceed 25 MiB");
            }
            var mimeType = detectMimeType(bytes);
            if (!mimeType.equals("application/pdf")
                    && !mimeType.equals(DOCX)
                    && !mimeType.equals("text/plain")) {
                throw new DocumentException(
                        "UNSUPPORTED_DOCUMENT_TYPE",
                        422,
                        "Only PDF, DOCX and UTF-8 text files are supported");
            }
            var checksum =
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .contentType(mimeType)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .build());
            return new StoredFile(bytes.length, mimeType, checksum);
        } catch (DocumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw storageFailure(exception);
        } finally {
            inspections.release();
        }
    }

    static String detectMimeType(byte[] bytes) {
        if (bytes.length >= 5
                && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "application/pdf";
        }
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') {
            return isDocx(bytes) ? DOCX : "application/octet-stream";
        }
        try {
            var text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            while (text.hasRemaining()) {
                var character = text.get();
                if (Character.isISOControl(character)
                        && character != '\n'
                        && character != '\r'
                        && character != '\t') {
                    return "application/octet-stream";
                }
            }
            return "text/plain";
        } catch (java.nio.charset.CharacterCodingException exception) {
            return "application/octet-stream";
        }
    }

    private static boolean isDocx(byte[] bytes) {
        java.nio.file.Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("knowledge-mime-", ".zip");
            Files.write(temporaryFile, bytes);
            // Read only package metadata; never inflate document text or embedded media here.
            try (var zip = new ZipFile(temporaryFile.toFile())) {
                var contentTypes = zip.getEntry("[Content_Types].xml");
                if (contentTypes == null || zip.getEntry("word/document.xml") == null) {
                    return false;
                }
                try (var stream = zip.getInputStream(contentTypes)) {
                    var content = stream.readNBytes(128 * 1024 + 1);
                    return content.length <= 128 * 1024
                            && new String(content, StandardCharsets.UTF_8)
                                    .contains(
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml");
                }
            }
        } catch (java.io.IOException exception) {
            return false;
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (java.io.IOException exception) {
                    org.slf4j.LoggerFactory.getLogger(MinioDocumentStorageAdapter.class)
                            .warn(
                                    "event=mime_temp_cleanup_failed errorCode=TEMP_FILE_CLEANUP_FAILED");
                }
            }
        }
    }

    private DocumentException storageFailure(Exception exception) {
        return new DocumentException(
                "STORAGE_UNAVAILABLE",
                503,
                "Object storage is unavailable; retry later",
                exception);
    }
}
