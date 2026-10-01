package com.portfolio.ticket.infrastructure.persistence;

import com.portfolio.ticket.domain.Ticket;
import com.portfolio.ticket.domain.TicketPage;
import com.portfolio.ticket.domain.TicketRepository;
import com.portfolio.ticket.domain.TicketSearchCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTicketRepositoryAdapter implements TicketRepository {

    private final TicketSpringDataRepository tickets;

    public JpaTicketRepositoryAdapter(TicketSpringDataRepository tickets) {
        this.tickets = tickets;
    }

    @Override
    public String nextNumber() {
        return "SUP-%08d".formatted(tickets.nextNumberSequenceValue());
    }

    @Override
    public Ticket create(Ticket ticket) {
        return tickets.saveAndFlush(TicketJpaEntity.fromDomain(ticket)).toDomain();
    }

    @Override
    public Ticket save(Ticket ticket) {
        return tickets.saveAndFlush(TicketJpaEntity.fromDomain(ticket)).toDomain();
    }

    @Override
    public Optional<Ticket> findByTenantIdAndId(UUID tenantId, UUID ticketId) {
        return tickets.findByTenantIdAndId(tenantId, ticketId).map(TicketJpaEntity::toDomain);
    }

    @Override
    public TicketPage search(TicketSearchCriteria criteria) {
        var direction = criteria.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable =
                PageRequest.of(
                        criteria.page(),
                        criteria.size(),
                        Sort.by(direction, criteria.sortProperty()));
        var result = tickets.findAll(specification(criteria), pageable);
        return new TicketPage(
                result.getContent().stream().map(TicketJpaEntity::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private Specification<TicketJpaEntity> specification(TicketSearchCriteria criteria) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(builder.equal(root.get("tenantId"), criteria.tenantId()));
            addEqualsFilterIfPresent(
                    predicates, builder, root, "requesterId", criteria.visibleRequesterId());
            addEqualsFilterIfPresent(predicates, builder, root, "status", criteria.status());
            addEqualsFilterIfPresent(predicates, builder, root, "priority", criteria.priority());
            addEqualsFilterIfPresent(predicates, builder, root, "category", criteria.category());
            addEqualsFilterIfPresent(
                    predicates, builder, root, "assigneeId", criteria.assigneeId());
            addEqualsFilterIfPresent(
                    predicates, builder, root, "requesterId", criteria.requesterId());
            if (criteria.createdFrom() != null) {
                predicates.add(
                        builder.greaterThanOrEqualTo(
                                root.get("createdAt"), criteria.createdFrom()));
            }
            if (criteria.createdTo() != null) {
                predicates.add(
                        builder.lessThanOrEqualTo(root.get("createdAt"), criteria.createdTo()));
            }
            if (criteria.query() != null) {
                var pattern = "%" + criteria.query().toLowerCase(java.util.Locale.ROOT) + "%";
                predicates.add(
                        builder.or(
                                builder.like(builder.lower(root.get("subject")), pattern),
                                builder.like(builder.lower(root.get("description")), pattern)));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private void addEqualsFilterIfPresent(
            List<Predicate> predicates,
            CriteriaBuilder builder,
            Root<TicketJpaEntity> root,
            String field,
            Object value) {
        if (value != null) {
            predicates.add(builder.equal(root.get(field), value));
        }
    }
}
