package com.portfolio.ticket.application;

import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketPage;
import com.portfolio.ticket.domain.TicketRepository;
import com.portfolio.ticket.domain.TicketSearchCriteria;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketQueryService {

    private static final Set<String> SORT_PROPERTIES =
            Set.of("updatedAt", "createdAt", "priority", "status", "number");

    private final TicketRepository tickets;
    private final TicketPolicy policy;

    public TicketQueryService(TicketRepository tickets, TicketPolicy policy) {
        this.tickets = tickets;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public Ticket get(AuthenticatedPrincipal principal, UUID ticketId) {
        var ticket =
                tickets.findByTenantIdAndId(principal.tenantId(), ticketId)
                        .orElseThrow(TicketNotFoundException::new);
        policy.requireCanView(principal, ticket);
        return ticket;
    }

    @Transactional(readOnly = true)
    public TicketPage list(AuthenticatedPrincipal principal, TicketQuery query) {
        var sort = parseSort(query.sort());
        var visibleRequester = policy.isSupport(principal) ? null : principal.userId();
        var criteria =
                new TicketSearchCriteria(
                        principal.tenantId(),
                        visibleRequester,
                        query.status(),
                        query.priority(),
                        query.category(),
                        query.assigneeId(),
                        query.requesterId(),
                        query.createdFrom(),
                        query.createdTo(),
                        normalizeQuery(query.query()),
                        query.page(),
                        query.size(),
                        sort.property(),
                        sort.ascending());
        return tickets.search(criteria);
    }

    private SortSpec parseSort(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SortSpec("updatedAt", false);
        }
        var parts = raw.split(",", -1);
        var property = parts[0];
        if (!SORT_PROPERTIES.contains(property) || parts.length > 2) {
            throw new IllegalArgumentException("Unsupported ticket sort");
        }
        var ascending = parts.length == 2 && "asc".equalsIgnoreCase(parts[1]);
        if (parts.length == 2
                && !"asc".equalsIgnoreCase(parts[1])
                && !"desc".equalsIgnoreCase(parts[1])) {
            throw new IllegalArgumentException("Sort direction must be asc or desc");
        }
        return new SortSpec(property, ascending);
    }

    private String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private record SortSpec(String property, boolean ascending) {}
}
