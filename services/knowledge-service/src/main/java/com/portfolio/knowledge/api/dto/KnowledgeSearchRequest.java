package com.portfolio.knowledge.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record KnowledgeSearchRequest(
        @NotNull UUID tenantId,
        @NotNull UUID principalId,
        @NotEmpty @Size(max = 3) Set<@NotBlank String> roles,
        @NotBlank @Size(max = 2000) String query,
        @Min(1) @Max(20) Integer topK,
        @Valid Filters filters) {
    public record Filters(@Size(max = 20) List<@NotBlank @Size(max = 50) String> tags) {}
}
