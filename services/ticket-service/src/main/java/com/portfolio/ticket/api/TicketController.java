package com.portfolio.ticket.api;

import com.portfolio.ticket.api.dto.CreateTicketRequest;
import com.portfolio.ticket.api.dto.TicketPageResponse;
import com.portfolio.ticket.api.dto.TicketResponse;
import com.portfolio.ticket.api.dto.UpdateTicketRequest;
import com.portfolio.ticket.application.AuthenticatedPrincipal;
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
            @Valid @RequestBody CreateTicketRequest request) {
        var ticket = commands.create(principal, request.toCommand());
        return ResponseEntity.created(URI.create("/v1/tickets/" + ticket.id()))
                .body(TicketResponse.from(ticket));
    }

    @GetMapping("/{ticketId}")
    public TicketResponse get(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID ticketId) {
        return TicketResponse.from(queries.get(principal, ticketId));
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
    public TicketResponse update(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID ticketId,
            @Valid @RequestBody UpdateTicketRequest request) {
        return TicketResponse.from(commands.update(principal, ticketId, request.toCommand()));
    }
}
