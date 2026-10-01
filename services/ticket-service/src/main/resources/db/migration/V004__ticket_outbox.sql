CREATE TABLE ticket.outbox_event (
  id uuid PRIMARY KEY,
  event_type varchar(100) NOT NULL,
  routing_key varchar(100) NOT NULL,
  tenant_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  payload jsonb NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED')),
  attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  next_attempt_at timestamptz NOT NULL,
  locked_at timestamptz,
  created_at timestamptz NOT NULL,
  published_at timestamptz,
  last_error varchar(500)
);

CREATE INDEX ix_outbox_event_ready
  ON ticket.outbox_event(next_attempt_at, created_at)
  WHERE status = 'PENDING';

CREATE INDEX ix_outbox_event_stale_claim
  ON ticket.outbox_event(locked_at)
  WHERE status = 'PROCESSING';
