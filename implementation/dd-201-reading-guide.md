# DD-201 Review Guide

This guide is the shortest path through Ticket CRUD/query. Read the production flow first and the
integration test last.

## Review order

1. `domain/TicketStatus` lists the lifecycle states.
2. `domain/TicketTest` shows the aggregate rules in small examples.
3. `domain/Ticket` owns state transitions and mutation invariants.
4. `application/TicketPolicy` answers who may view, edit, manage, or transition a ticket.
5. `application/TicketCommandService` orchestrates write use cases and transaction boundaries.
6. `application/TicketQueryService` builds tenant-safe query criteria.
7. `api/TicketController` maps HTTP input to application commands and queries.
8. `infrastructure/persistence/JpaTicketRepositoryAdapter` implements the repository port.
9. `TicketCrudIT` verifies that all layers work together against PostgreSQL.

## Create flow

```text
POST /v1/tickets
  -> CreateTicketRequest
  -> TicketCommandService.create
  -> Ticket.open (new tickets always start OPEN)
  -> TicketRepository.create
  -> PostgreSQL
```

`tenantId` and `requesterId` come from `AuthenticatedPrincipal`, not the request body. A missing
priority becomes `MEDIUM` in the application service.

## Update flow

```text
PATCH /v1/tickets/{id}
  -> UpdateTicketRequest
  -> TicketCommandService.update
       1. load by tenant and id
       2. apply content changes
       3. apply support-managed fields
       4. apply status change
  -> TicketRepository.save
```

Each group has one owner:

| Concern | Owner |
|---|---|
| Request shape and size limits | API DTO |
| Caller and role authorization | `TicketPolicy` |
| Allowed state transitions | `Ticket` |
| Transaction and operation order | `TicketCommandService` |
| Tenant-scoped reads/writes | Repository port and adapter |

Assignment is applied before an explicit status change because assigning an OPEN ticket starts work
and moves it to `IN_PROGRESS`.

## Query flow

`TicketQueryService` always inserts the principal tenant into `TicketSearchCriteria`. For CUSTOMER,
it also inserts the caller as `visibleRequesterId`; a client-provided requester filter can narrow
the result but can never widen visibility.

## Test intent

- `TicketTest` is the executable state-machine specification.
- `ResourceServerSecurityTest` verifies JWT/JWKS trust and principal conversion.
- `TicketCrudIT` uses named actors such as `ACME_CUSTOMER` and semantic helpers such as
  `changeStatusAs`; each test describes one externally visible rule.

DD-202 will add idempotent create and ETag/`If-Match`. Those concerns should not be mixed into the
DD-201 domain rules.
