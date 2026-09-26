package com.portfolio.ticket.domain;

import java.util.List;

public record TicketPage(
        List<Ticket> content, int page, int size, long totalElements, int totalPages) {

    public TicketPage {
        content = List.copyOf(content);
    }
}
