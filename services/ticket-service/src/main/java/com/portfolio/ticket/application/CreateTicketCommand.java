package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.Priority;

public record CreateTicketCommand(String subject, String description, Priority priority) {}
