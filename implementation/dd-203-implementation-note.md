# DD-203 Implementation Note

## Delivered behavior

- `POST /v1/tickets/{ticketId}/comments` appends a public or internal comment and returns the new
  ticket ETag.
- `GET /v1/tickets/{ticketId}/comments` returns comments in chronological order.
- Customers can add and see public comments only on their own tickets.
- Agent/Admin callers can add and see public and internal comments in their tenant.
- Closed tickets reject new comments until an Admin reopens the ticket.
- Ticket creation, actual field changes, assignment-driven status changes, and added comments write
  append-only audit rows in the same transaction as the mutation.
- Content audit records intentionally omit subject/description values; the audit says content
  changed without copying sensitive ticket text.

## Why these classes exist

- `TicketComment` represents one immutable conversation entry.
- `TicketHistoryRepository` is the single persistence contract for append-only comments and audit.
- `JdbcTicketHistoryRepository` contains the corresponding SQL in one place.
- `TicketAuditRecorder` compares ticket state and translates real changes into audit actions. This
  keeps `TicketCommandService` focused on the readable create, update, and add-comment flows.
- `AddCommentRequest` and `TicketCommentResponse` are the transport shapes. No extra comment service
  or one-method command class was introduced.

## Short review path

1. Read `TicketCrudIT.hidesInternalCommentsFromCustomers` and `auditsTicketMutations`.
2. Read the two comment endpoints in `TicketController`.
3. Read `TicketCommandService.addComment` and `TicketQueryService.listComments`.
4. Read `TicketPolicy.requireCanAddInternalComment`.
5. Read `TicketAuditRecorder`, then `JdbcTicketHistoryRepository` and Flyway `V003`.

## Verification

`./mvnw.cmd -pl services/ticket-service -am clean verify` passed with 11 unit/security tests and 13
PostgreSQL Testcontainers integration tests. Flyway successfully applied V001, V002, and V003.
