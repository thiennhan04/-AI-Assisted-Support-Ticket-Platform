package com.portfolio.knowledge.domain;

public record Chunk(
        int ordinal,
        Integer pageNumber,
        String heading,
        String content,
        String contentChecksum,
        int tokenCount) {}
