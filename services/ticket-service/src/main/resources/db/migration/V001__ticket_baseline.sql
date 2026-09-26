CREATE SCHEMA IF NOT EXISTS ticket;
CREATE SEQUENCE ticket.ticket_number_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE ticket.ticket (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_number varchar(30) NOT NULL,
  requester_id uuid NOT NULL,
  assignee_id uuid,
  subject varchar(300) NOT NULL CHECK (char_length(subject) BETWEEN 3 AND 300),
  description text NOT NULL CHECK (char_length(description) BETWEEN 10 AND 20000),
  category varchar(30) CHECK (category IN ('ACCOUNT', 'PAYMENT', 'TECHNICAL', 'GENERAL', 'OTHER')),
  priority varchar(20) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
  status varchar(30) NOT NULL CHECK (
    status IN ('OPEN', 'IN_PROGRESS', 'WAITING_CUSTOMER', 'RESOLVED', 'CLOSED')
  ),
  content_version bigint NOT NULL DEFAULT 0 CHECK (content_version >= 0),
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  closed_at timestamptz,
  UNIQUE (tenant_id, ticket_number)
);

CREATE INDEX ix_ticket_status_updated
  ON ticket.ticket(tenant_id, status, updated_at DESC);
CREATE INDEX ix_ticket_assignee_status
  ON ticket.ticket(tenant_id, assignee_id, status);
CREATE INDEX ix_ticket_requester_created
  ON ticket.ticket(tenant_id, requester_id, created_at DESC);
CREATE INDEX ix_ticket_search ON ticket.ticket USING gin
  (to_tsvector('simple', coalesce(subject, '') || ' ' || coalesce(description, '')));
