-- Local demo snapshots for the existing acme tenant and demo users.
-- Run against ticket_db only, after Ticket Service migrations have completed.
-- Fixed IDs make reruns safe: existing tickets and comments are left unchanged.
-- Direct fixtures do not emit outbox events or simulate a complete mutation history.

BEGIN;

DO $$
BEGIN
  IF current_database() <> 'ticket_db' THEN
    RAISE EXCEPTION 'This script must run against ticket_db';
  END IF;
END
$$;

WITH samples(id, subject, description, category, priority, status, age_days) AS (
  VALUES
    ('20000000-0000-4000-8000-000000000001'::uuid,
     'Demo: Cannot reset password',
     'The password reset email does not arrive after multiple attempts.',
     'ACCOUNT', 'HIGH', 'OPEN', 1),
    ('20000000-0000-4000-8000-000000000002'::uuid,
     'Demo: Charged twice for subscription',
     'Two subscription charges appeared on the same billing statement.',
     'PAYMENT', 'URGENT', 'IN_PROGRESS', 2),
    ('20000000-0000-4000-8000-000000000003'::uuid,
     'Demo: Unable to upload a document',
     'Document upload fails with an error when submitting a PDF file.',
     'TECHNICAL', 'MEDIUM', 'WAITING_CUSTOMER', 3),
    ('20000000-0000-4000-8000-000000000004'::uuid,
     'Demo: Update billing address',
     'The customer requested help updating the billing address on the account.',
     'GENERAL', 'LOW', 'RESOLVED', 4),
    ('20000000-0000-4000-8000-000000000005'::uuid,
     'Demo: Export account data',
     'The customer requested a copy of account data and confirmed receipt.',
     'OTHER', 'LOW', 'CLOSED', 5)
), inserted AS (
  INSERT INTO ticket.ticket
    (id, tenant_id, ticket_number, requester_id, assignee_id,
     subject, description, category, priority, status,
     content_version, version, created_at, updated_at, closed_at)
  SELECT s.id,
         '00000000-0000-4000-8000-000000000001'::uuid,
         'SUP-' || lpad(nextval('ticket.ticket_number_seq')::text, 8, '0'),
         '00000000-0000-4000-8000-000000000103'::uuid,
         CASE WHEN s.status = 'OPEN' THEN NULL
              ELSE '00000000-0000-4000-8000-000000000102'::uuid END,
         s.subject, s.description, s.category, s.priority, s.status,
         0, 0, now() - s.age_days * interval '1 day', now(),
         CASE WHEN s.status = 'CLOSED' THEN now() ELSE NULL END
    FROM samples s
   WHERE NOT EXISTS (SELECT 1 FROM ticket.ticket t WHERE t.id = s.id)
  ON CONFLICT (id) DO NOTHING
  RETURNING id, tenant_id, requester_id, ticket_number, created_at
)
INSERT INTO ticket.ticket_audit
  (id, tenant_id, ticket_id, actor_id, action, old_value, new_value, occurred_at)
SELECT gen_random_uuid(), tenant_id, id, requester_id,
       'TICKET_CREATED', NULL, ticket_number, created_at
  FROM inserted;

WITH samples(id, ticket_id, author_id, body, internal, minutes_ago) AS (
  VALUES
    ('30000000-0000-4000-8000-000000000001'::uuid,
     '20000000-0000-4000-8000-000000000002'::uuid,
     '00000000-0000-4000-8000-000000000103'::uuid,
     'Both charges have the same amount and were made on the same day.', false, 30),
    ('30000000-0000-4000-8000-000000000002'::uuid,
     '20000000-0000-4000-8000-000000000002'::uuid,
     '00000000-0000-4000-8000-000000000102'::uuid,
     'Check payment provider records before issuing a refund.', true, 20),
    ('30000000-0000-4000-8000-000000000003'::uuid,
     '20000000-0000-4000-8000-000000000003'::uuid,
     '00000000-0000-4000-8000-000000000102'::uuid,
     'Please share the file size and the exact error message.', false, 10)
), inserted AS (
  INSERT INTO ticket.ticket_comment
    (id, tenant_id, ticket_id, author_id, body, internal, created_at)
  SELECT s.id, t.tenant_id, s.ticket_id, s.author_id, s.body, s.internal,
         now() - s.minutes_ago * interval '1 minute'
    FROM samples s
    JOIN ticket.ticket t ON t.id = s.ticket_id
   WHERE t.tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
     AND NOT EXISTS (SELECT 1 FROM ticket.ticket_comment c WHERE c.id = s.id)
  ON CONFLICT (id) DO NOTHING
  RETURNING id, tenant_id, ticket_id, author_id, created_at
)
INSERT INTO ticket.ticket_audit
  (id, tenant_id, ticket_id, actor_id, action, old_value, new_value, occurred_at)
SELECT gen_random_uuid(), tenant_id, ticket_id, author_id,
       'COMMENT_ADDED', NULL, id::text, created_at
  FROM inserted;

COMMIT;
