package com.portfolio.knowledge.api.dto;

import com.portfolio.knowledge.domain.DocumentAccessScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 300) String title,
        DocumentAccessScope visibility,
        @Size(max = 20) List<@NotBlank @Size(max = 50) String> tags) {}
