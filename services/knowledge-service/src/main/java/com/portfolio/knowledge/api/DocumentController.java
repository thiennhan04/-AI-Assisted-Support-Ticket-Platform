package com.portfolio.knowledge.api;

import com.portfolio.knowledge.api.dto.CreateDocumentRequest;
import com.portfolio.knowledge.api.dto.DocumentResponse;
import com.portfolio.knowledge.application.AuthenticatedPrincipal;
import com.portfolio.knowledge.application.DocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/v1/knowledge/documents")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @AuthenticationPrincipal AuthenticatedPrincipal caller,
            @Valid @RequestPart("metadata") CreateDocumentRequest request,
            @RequestPart("file") MultipartFile file) {
        return DocumentResponse.from(service.upload(caller, request, file));
    }

    @GetMapping("/{id}")
    public DocumentResponse get(
            @AuthenticationPrincipal AuthenticatedPrincipal caller, @PathVariable UUID id) {
        return DocumentResponse.from(service.get(caller, id));
    }

    @GetMapping
    public List<DocumentResponse> list(
            @AuthenticationPrincipal AuthenticatedPrincipal caller,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(caller, page, size).stream().map(DocumentResponse::from).toList();
    }
}
