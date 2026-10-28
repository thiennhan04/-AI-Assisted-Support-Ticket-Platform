package com.portfolio.knowledge.application;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorage {
    StoredFile upload(String objectKey, MultipartFile file);

    byte[] download(String objectKey);

    void delete(String objectKey);

    record StoredFile(long sizeBytes, String mimeType, String sha256) {}
}
