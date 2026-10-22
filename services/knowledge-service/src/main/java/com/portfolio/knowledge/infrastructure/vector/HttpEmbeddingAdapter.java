package com.portfolio.knowledge.infrastructure.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolio.knowledge.application.EmbeddingPort;
import com.portfolio.knowledge.domain.DocumentException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "knowledge.embedding.provider", havingValue = "http")
public class HttpEmbeddingAdapter implements EmbeddingPort {
    private final RestClient client;
    private final String model;
    private final String modelId;
    private final URI endpoint;

    public HttpEmbeddingAdapter(
            @Value("${knowledge.embedding.url}") URI endpoint,
            @Value("${knowledge.embedding.api-key}") String apiKey,
            @Value("${knowledge.embedding.model}") String model,
            @Value("${knowledge.embedding.space-id}") String spaceId) {
        if (!"https".equals(endpoint.getScheme())
                || endpoint.getUserInfo() != null
                || apiKey.isBlank()
                || model.isBlank()
                || !spaceId.matches("[a-zA-Z0-9._-]{1,40}")) {
            throw new IllegalArgumentException(
                    "HTTP embeddings require HTTPS, credentials, model and space ID");
        }
        this.endpoint = endpoint;
        this.model = model;
        this.modelId = "http/" + spaceId + "/" + model;
        if (modelId.length() > 100) {
            throw new IllegalArgumentException("Embedding model ID must not exceed 100 characters");
        }
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        client =
                RestClient.builder()
                        .requestFactory(factory)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .build();
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        var request =
                Map.of(
                        "model",
                        model,
                        "input",
                        texts,
                        "dimensions",
                        DIMENSIONS,
                        "encoding_format",
                        "float");
        JsonNode response;
        try {
            response =
                    client.post()
                            .uri(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(JsonNode.class);
        } catch (RuntimeException exception) {
            // Do not retain provider response bodies, credentials or document text in
            // exceptions/logs.
            throw new DocumentException(
                    "EMBEDDING_UNAVAILABLE", 503, "Embedding provider is unavailable; retry later");
        }
        return readVectors(response, texts.size());
    }

    List<float[]> readVectors(JsonNode response, int expectedCount) {
        if (response == null
                || !model.equals(response.path("model").asText())
                || !response.path("data").isArray()
                || response.path("data").size() != expectedCount) {
            throw EmbeddingPort.invalidResponse();
        }
        var ordered = new float[expectedCount][];
        for (var item : response.path("data")) {
            var position = item.path("index");
            if (!position.isIntegralNumber()) {
                throw EmbeddingPort.invalidResponse();
            }
            int index = position.asInt(-1);
            var values = item.path("embedding");
            if (index < 0
                    || index >= ordered.length
                    || ordered[index] != null
                    || !values.isArray()
                    || values.size() != DIMENSIONS) {
                throw EmbeddingPort.invalidResponse();
            }
            var vector = new float[DIMENSIONS];
            for (int i = 0; i < DIMENSIONS; i++) {
                if (!values.get(i).isNumber()) {
                    throw EmbeddingPort.invalidResponse();
                }
                vector[i] = (float) values.get(i).asDouble();
            }
            EmbeddingPort.validate(vector);
            ordered[index] = vector;
        }
        return Arrays.asList(ordered);
    }
}
