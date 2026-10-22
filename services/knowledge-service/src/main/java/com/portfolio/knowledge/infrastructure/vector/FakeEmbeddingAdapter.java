package com.portfolio.knowledge.infrastructure.vector;

import com.portfolio.knowledge.application.EmbeddingPort;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "knowledge.embedding.provider",
        havingValue = "fake",
        matchIfMissing = true)
public class FakeEmbeddingAdapter implements EmbeddingPort {
    @Override
    public String modelId() {
        return "fake-word-hash-v1";
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        var vectors = new ArrayList<float[]>();
        for (var text : texts) {
            var vector = new float[DIMENSIONS];
            var normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
            normalized = normalized.toLowerCase(Locale.ROOT);
            var words = normalized.split("[^\\p{L}\\p{N}]+");
            for (var word : words) {
                if (!word.isBlank()) {
                    int bucket = Math.floorMod(word.hashCode(), DIMENSIONS);
                    vector[bucket] += 1;
                }
            }
            double squaredLength = 0;
            for (float value : vector) {
                squaredLength += (double) value * value;
            }
            if (squaredLength == 0) {
                vector[0] = 1;
                squaredLength = 1;
            }
            double length = Math.sqrt(squaredLength);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= (float) length;
            }
            vectors.add(vector);
        }
        return List.copyOf(vectors);
    }
}
