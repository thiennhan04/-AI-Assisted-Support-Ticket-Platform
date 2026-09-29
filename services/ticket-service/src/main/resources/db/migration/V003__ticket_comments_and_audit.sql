CREATE TABLE ticket.ticket_comment (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_id uuid NOT NULL REFERENCES ticket.ticket(id),
  author_id uuid NOT NULL,
  body text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 10000),
  internal boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL
);

CREATE INDEX ix_ticket_comment_timeline
  ON ticket.ticket_comment(tenant_id, ticket_id, created_at, id);

CREATE TABLE ticket.ticket_audit (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  ticket_id uuid NOT NULL REFERENCES ticket.ticket(id),
  actor_id uuid NOT NULL,
  action varchar(40) NOT NULL CHECK (action IN (
    'TICKET_CREATED',
    'CONTENT_UPDATED',
    'PRIORITY_CHANGED',
    'CATEGORY_CHANGED',
    'ASSIGNEE_CHANGED',
    'STATUS_CHANGED',
    'COMMENT_ADDED'
  )),
  old_value varchar(100),
  new_value varchar(100),
  occurred_at timestamptz NOT NULL
);

CREATE INDEX ix_ticket_audit_timeline
  ON ticket.ticket_audit(tenant_id, ticket_id, occurred_at, id);
