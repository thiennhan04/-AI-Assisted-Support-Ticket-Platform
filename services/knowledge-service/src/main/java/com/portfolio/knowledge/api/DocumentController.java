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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal AuthenticatedPrincipal caller,
            @Valid @RequestPart("metadata") CreateDocumentRequest request,
            @RequestPart("file") MultipartFile file) {
        var document = service.upload(caller, request, file);
        var response = DocumentResponse.from(document);
        var etag = "\"" + document.version().versionNumber() + "\"";
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(
            @AuthenticationPrincipal AuthenticatedPrincipal caller, @PathVariable UUID id) {
        var document = service.get(caller, id);
        var response = DocumentResponse.from(document);
        var etag = "\"" + document.version().versionNumber() + "\"";
        return ResponseEntity.ok().eTag(etag).body(response);
    }

    @PostMapping(value = "/{id}/versions", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> replace(
            @AuthenticationPrincipal AuthenticatedPrincipal caller,
            @PathVariable UUID id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestPart("file") MultipartFile file) {
        var document = service.replace(caller, id, ifMatch, file);
        var response = DocumentResponse.from(document);
        var etag = "\"" + document.version().versionNumber() + "\"";
        return ResponseEntity.status(HttpStatus.CREATED).eTag(etag).body(response);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void delete(
            @AuthenticationPrincipal AuthenticatedPrincipal caller, @PathVariable UUID id) {
        service.delete(caller, id);
    }

    @GetMapping
    public List<DocumentResponse> list(
            @AuthenticationPrincipal AuthenticatedPrincipal caller,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(caller, page, size).stream().map(DocumentResponse::from).toList();
    }
}
