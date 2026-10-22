package com.portfolio.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SearchServiceTest {
    private final EmbeddingPort embedding = mock(EmbeddingPort.class);
    private final KnowledgeIndexRepository index = mock(KnowledgeIndexRepository.class);
    private final SearchService service = new SearchService(embedding, index);
    private final AuthenticatedPrincipal caller =
            new AuthenticatedPrincipal(
                    UUID.randomUUID(), UUID.randomUUID(), Set.of(Roles.CUSTOMER));

    @Test
    void passesVerifiedIdentityTagsAndNormalizedQueryToSearch() {
        var vector = new float[EmbeddingPort.DIMENSIONS];
        vector[0] = 1;
        when(embedding.modelId()).thenReturn("model");
        when(embedding.embed(List.of("password reset"))).thenReturn(List.of(vector));
        when(index.search(
                        caller.tenantId(),
                        caller.userId(),
                        caller.roles(),
                        "model",
                        vector,
                        List.of("account"),
                        5))
                .thenReturn(List.of());
        assertThat(service.search(caller, " password\n reset ", 5, List.of("account"))).isEmpty();
        verify(index)
                .search(
                        caller.tenantId(),
                        caller.userId(),
                        caller.roles(),
                        "model",
                        vector,
                        List.of("account"),
                        5);
    }

    @Test
    void invalidQueryAndRolesDoNotCallProvider() {
        assertThatThrownBy(() -> service.search(caller, " ", 5, List.of()))
                .isInstanceOf(DocumentException.class);
        assertThatThrownBy(() -> service.search(caller, "password", 21, List.of()))
                .isInstanceOf(DocumentException.class);
        var invalid =
                new AuthenticatedPrincipal(caller.userId(), caller.tenantId(), Set.of("UNKNOWN"));
        assertThatThrownBy(() -> service.search(invalid, "password", 5, List.of()))
                .isInstanceOf(DocumentException.class);
        verifyNoInteractions(embedding, index);
    }
}
