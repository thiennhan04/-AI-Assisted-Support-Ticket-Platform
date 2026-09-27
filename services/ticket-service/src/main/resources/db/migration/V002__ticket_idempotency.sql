CREATE TABLE ticket.idempotency_record (
  tenant_id uuid NOT NULL,
  requester_id uuid NOT NULL,
  idempotency_key uuid NOT NULL,
  request_hash char(64) NOT NULL,
  ticket_id uuid NOT NULL REFERENCES ticket.ticket(id),
  created_at timestamptz NOT NULL,
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (tenant_id, requester_id, idempotency_key)
);

CREATE INDEX ix_idempotency_record_expiry
  ON ticket.idempotency_record(expires_at);
