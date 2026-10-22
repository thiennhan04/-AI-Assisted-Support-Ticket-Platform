package com.portfolio.knowledge.application;

import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository.SearchMatch;
import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SearchService {
    private static final Set<String> KNOWN_ROLES = Set.of(Roles.ADMIN, Roles.AGENT, Roles.CUSTOMER);
    private final EmbeddingPort embedding;
    private final KnowledgeIndexRepository index;

    public SearchService(EmbeddingPort embedding, KnowledgeIndexRepository index) {
        this.embedding = embedding;
        this.index = index;
    }

    public List<SearchMatch> search(
            AuthenticatedPrincipal caller, String query, int topK, List<String> tags) {
        if (caller == null
                || caller.tenantId() == null
                || caller.userId() == null
                || caller.roles().isEmpty()
                || !KNOWN_ROLES.containsAll(caller.roles())) {
            throw new DocumentException("SEARCH_FORBIDDEN", 403, "A verified caller is required");
        }
        if (query == null || query.isBlank() || query.length() > 2000 || topK < 1 || topK > 20) {
            throw new DocumentException("INVALID_SEARCH", 400, "Query and topK are invalid");
        }
        var selectedTags = tags == null ? List.<String>of() : List.copyOf(tags);
        if (selectedTags.size() > 20) {
            throw new DocumentException("INVALID_SEARCH", 400, "Too many tags");
        }
        for (var tag : selectedTags) {
            if (tag.isBlank() || tag.length() > 50) {
                throw new DocumentException("INVALID_SEARCH", 400, "Search tags are invalid");
            }
        }
        var normalized = Normalizer.normalize(query, Normalizer.Form.NFC);
        normalized = normalized.replaceAll("\\s+", " ").strip();
        var vectors = embedding.embed(List.of(normalized));
        if (vectors == null || vectors.size() != 1) {
            throw EmbeddingPort.invalidResponse();
        }
        var queryVector = vectors.getFirst();
        EmbeddingPort.validate(queryVector);
        var results =
                index.search(
                        caller.tenantId(),
                        caller.userId(),
                        caller.roles(),
                        embedding.modelId(),
                        queryVector,
                        selectedTags,
                        topK);
        return results;
    }
}
