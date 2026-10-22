package com.portfolio.knowledge.infrastructure.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.knowledge.application.EmbeddingPort;
import com.portfolio.knowledge.domain.DocumentException;
import java.net.URI;
import org.junit.jupiter.api.Test;

class HttpEmbeddingAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpEmbeddingAdapter adapter =
            new HttpEmbeddingAdapter(
                    URI.create("https://example.invalid/embeddings"),
                    "test-key",
                    "test-model",
                    "tests");

    @Test
    void restoresProviderIndexOrderAndValidatesDimensions() {
        var response = response();
        var data = response.putArray("data");
        data.add(item(1, 0.5f));
        data.add(item(0, 1));
        var vectors = adapter.readVectors(response, 2);
        assertThat(vectors.get(0)[0]).isEqualTo(1);
        assertThat(vectors.get(1)[0]).isEqualTo(0.5f);
    }

    @Test
    void rejectsDuplicateIndicesWrongModelMissingItemsAndNonNumericValues() {
        var duplicate = response();
        duplicate.putArray("data").add(item(0, 1)).add(item(0, 1));
        assertThatThrownBy(() -> adapter.readVectors(duplicate, 2))
                .isInstanceOf(DocumentException.class);
        var wrongModel = response();
        wrongModel.put("model", "another-model");
        wrongModel.putArray("data").add(item(0, 1));
        assertThatThrownBy(() -> adapter.readVectors(wrongModel, 1))
                .isInstanceOf(DocumentException.class);
        var missing = response();
        missing.putArray("data");
        assertThatThrownBy(() -> adapter.readVectors(missing, 1))
                .isInstanceOf(DocumentException.class);
        var malformed = response();
        var item = item(0, 1);
        item.withArray("embedding")
                .set(0, mapper.getNodeFactory().textNode("secret-provider-error"));
        malformed.putArray("data").add(item);
        assertThatThrownBy(() -> adapter.readVectors(malformed, 1))
                .isInstanceOf(DocumentException.class)
                .hasMessageNotContaining("secret-provider-error");
    }

    private ObjectNode response() {
        var response = mapper.createObjectNode();
        response.put("model", "test-model");
        return response;
    }

    private ObjectNode item(int index, float first) {
        var item = mapper.createObjectNode();
        item.put("index", index);
        var vector = item.putArray("embedding");
        vector.add(first);
        for (int i = 1; i < EmbeddingPort.DIMENSIONS; i++) {
            vector.add(0);
        }
        return item;
    }
}
