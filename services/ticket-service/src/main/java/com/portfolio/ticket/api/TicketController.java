package com.portfolio.ticket.api;

import com.portfolio.ticket.api.dto.AddCommentRequest;
import com.portfolio.ticket.api.dto.CreateTicketRequest;
import com.portfolio.ticket.api.dto.TicketCommentResponse;
import com.portfolio.ticket.api.dto.TicketPageResponse;
import com.portfolio.ticket.api.dto.TicketResponse;
import com.portfolio.ticket.api.dto.UpdateTicketRequest;
import com.portfolio.ticket.application.AuthenticatedPrincipal;
import com.portfolio.ticket.application.TicketCommandException;
import com.portfolio.ticket.application.TicketCommandService;
import com.portfolio.ticket.application.TicketQuery;
import com.portfolio.ticket.application.TicketQueryService;
import com.portfolio.ticket.domain.Category;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.TicketStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/tickets")
public class TicketController {

    private final TicketCommandService commands;
    private final TicketQueryService queries;

    public TicketController(TicketCommandService commands, TicketQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreateTicketRequest request) {
        var ticket = commands.create(principal, idempotencyKey, request.toCommand());
        return ResponseEntity.created(URI.create("/v1/tickets/" + ticket.id()))
                .eTag(etag(ticket.version()))
                .body(TicketResponse.from(ticket));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> get(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID ticketId) {
        var ticket = queries.get(principal, ticketId);
        return ResponseEntity.ok().eTag(etag(ticket.version())).body(TicketResponse.from(ticket));
    }

    @GetMapping
    public TicketPageResponse list(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID requesterId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant createdTo,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "updatedAt,desc") String sort) {
        return TicketPageResponse.from(
                queries.list(
                        principal,
                        new TicketQuery(
                                status,
                                priority,
                                category,
                                assigneeId,
                                requesterId,
                                createdFrom,
                                createdTo,
                                q,
                                page,
                                size,
                                sort)));
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<TicketResponse> update(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID ticketId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateTicketRequest request) {
        var ticket =
                commands.update(principal, ticketId, expectedVersion(ifMatch), request.toCommand());
        return ResponseEntity.ok().eTag(etag(ticket.version())).body(TicketResponse.from(ticket));
    }

    @PostMapping("/{ticketId}/comments")
    public ResponseEntity<TicketCommentResponse> addComment(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID ticketId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody AddCommentRequest request) {
        var result =
                commands.addComment(
                        principal,
                        ticketId,
                        expectedVersion(ifMatch),
                        request.body(),
                        request.internal());
        return ResponseEntity.created(
                        URI.create(
                                "/v1/tickets/" + ticketId + "/comments/" + result.comment().id()))
                .eTag(etag(result.ticketVersion()))
                .body(TicketCommentResponse.from(result.comment()));
    }

    @GetMapping("/{ticketId}/comments")
    public List<TicketCommentResponse> listComments(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID ticketId) {
        return queries.listComments(principal, ticketId).stream()
                .map(TicketCommentResponse::from)
                .toList();
    }

    private long expectedVersion(String ifMatch) {
        if (ifMatch == null) {
            throw TicketCommandException.preconditionRequired();
        }
        if (!ifMatch.matches("\"[0-9]+\"")) {
            throw new IllegalArgumentException("If-Match must be a quoted ticket version");
        }
        return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }
}
