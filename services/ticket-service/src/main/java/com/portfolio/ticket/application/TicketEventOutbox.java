package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.Ticket;

public interface TicketEventOutbox {

    void appendTicketCreated(Ticket ticket);
}
