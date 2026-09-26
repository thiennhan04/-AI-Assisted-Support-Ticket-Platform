package com.portfolio.ticket.api.dto;

import com.portfolio.ticket.domain.TicketPage;
import java.util.List;

public record TicketPageResponse(
        List<TicketResponse> content, int page, int size, long totalElements, int totalPages) {

    public static TicketPageResponse from(TicketPage result) {
        return new TicketPageResponse(
                result.content().stream().map(TicketResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
