package com.portfolio.knowledge.api;

import com.portfolio.knowledge.api.dto.KnowledgeSearchRequest;
import com.portfolio.knowledge.application.SearchService;
import com.portfolio.knowledge.domain.DocumentException;
import com.portfolio.knowledge.domain.KnowledgeIndexRepository.SearchMatch;
import com.portfolio.knowledge.infrastructure.security.ServiceJwtClaimsValidator;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/knowledge/search")
public class KnowledgeSearchInternalController {
    private final SearchService service;

    public KnowledgeSearchInternalController(SearchService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_knowledge:search')")
    public List<SearchMatch> search(
            @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody KnowledgeSearchRequest request) {
        var caller = ServiceJwtClaimsValidator.delegatedCaller(token);
        boolean sameTenant = caller.tenantId().equals(request.tenantId());
        boolean sameUser = caller.userId().equals(request.principalId());
        boolean sameRoles = caller.roles().equals(request.roles());
        if (!sameTenant || !sameUser || !sameRoles) {
            throw new DocumentException(
                    "SEARCH_FORBIDDEN", 403, "Search caller must match the signed delegation");
        }
        int topK = request.topK() == null ? 8 : request.topK();
        var tags = List.<String>of();
        if (request.filters() != null && request.filters().tags() != null) {
            tags = request.filters().tags();
        }
        var matches = service.search(caller, request.query(), topK, tags);
        return matches;
    }
}
