package com.portfolio.ticket.domain;

import java.util.Optional;
import java.util.UUID;

public interface TicketRepository {

    String nextNumber();

    Ticket create(Ticket ticket);

    Ticket save(Ticket ticket);

    Optional<Ticket> findByTenantIdAndId(UUID tenantId, UUID ticketId);

    TicketPage search(TicketSearchCriteria criteria);
}
