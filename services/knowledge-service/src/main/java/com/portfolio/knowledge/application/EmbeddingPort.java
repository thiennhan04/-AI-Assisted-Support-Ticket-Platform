package com.portfolio.knowledge.application;

import com.portfolio.knowledge.domain.DocumentException;
import java.util.List;

public interface EmbeddingPort {
    int DIMENSIONS = 1536;

    String modelId();

    List<float[]> embed(List<String> texts);

    static void validate(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS) {
            throw invalidResponse();
        }
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw invalidResponse();
            }
            norm += (double) value * value;
        }
        if (norm == 0) {
            throw invalidResponse();
        }
    }

    static DocumentException invalidResponse() {
        return new DocumentException(
                "EMBEDDING_INVALID_RESPONSE", 503, "Embedding provider returned invalid vectors");
    }
}
