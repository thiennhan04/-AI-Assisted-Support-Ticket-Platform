package com.portfolio.ticket.api.dto;

import com.portfolio.ticket.application.CreateTicketCommand;
import com.portfolio.ticket.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
        @NotBlank @Size(min = 3, max = 300) String subject,
        @NotBlank @Size(min = 10, max = 20_000) String description,
        Priority priority) {

    public CreateTicketCommand toCommand() {
        return new CreateTicketCommand(subject, description, priority);
    }
}
