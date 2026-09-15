ALTER TABLE identity.refresh_session
  ADD CONSTRAINT fk_refresh_session_replacement
  FOREIGN KEY (replaced_by_id) REFERENCES identity.refresh_session(id);

CREATE UNIQUE INDEX ux_refresh_session_replaced_by
  ON identity.refresh_session(replaced_by_id)
  WHERE replaced_by_id IS NOT NULL;
