package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.Category;
import com.portfolio.ticket.domain.Priority;
import com.portfolio.ticket.domain.TicketStatus;
import java.util.UUID;

public record UpdateTicketCommand(
        String subject,
        String description,
        Category category,
        Priority priority,
        TicketStatus status,
        UUID assigneeId) {}
