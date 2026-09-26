package com.portfolio.ticket.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.portfolio.ticket.application.UpdateTicketCommand;
import com.portfolio.ticket.domain.Category;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.TicketStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateTicketRequest(
        @Size(min = 3, max = 300) String subject,
        @Size(min = 10, max = 20_000) String description,
        Category category,
        Priority priority,
        TicketStatus status,
        UUID assigneeId) {

    @JsonIgnore
    @AssertTrue(message = "at least one ticket field is required") public boolean isNotEmpty() {
        return subject != null
                || description != null
                || category != null
                || priority != null
                || status != null
                || assigneeId != null;
    }

    public UpdateTicketCommand toCommand() {
        return new UpdateTicketCommand(
                subject, description, category, priority, status, assigneeId);
    }
}
