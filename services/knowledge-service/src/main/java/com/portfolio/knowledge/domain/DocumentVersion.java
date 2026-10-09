package com.portfolio.knowledge.domain;

import java.util.UUID;

public record DocumentVersion(
        UUID id,
        int versionNumber,
        String objectKey,
        String mimeType,
        long sizeBytes,
        String sha256,
        DocumentStatus status,
        String errorCode) {}
