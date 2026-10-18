package com.portfolio.knowledge.application;

import java.util.List;

public interface TextExtractor {
    List<Section> extract(byte[] bytes, String mimeType);

    record Section(Integer pageNumber, String heading, String text) {}
}
