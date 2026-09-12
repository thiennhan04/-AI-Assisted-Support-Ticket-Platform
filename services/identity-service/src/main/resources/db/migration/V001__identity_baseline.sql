CREATE EXTENSION IF NOT EXISTS citext;
CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.tenant (
  id uuid PRIMARY KEY,
  code varchar(50) NOT NULL UNIQUE,
  name varchar(200) NOT NULL,
  status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE identity.app_user (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES identity.tenant(id),
  email citext NOT NULL,
  password_hash varchar(255) NOT NULL,
  display_name varchar(200) NOT NULL,
  status varchar(20) NOT NULL CHECK (status IN ('INVITED', 'ACTIVE', 'LOCKED', 'DISABLED')),
  failed_login_count integer NOT NULL DEFAULT 0 CHECK (failed_login_count >= 0),
  locked_until timestamptz,
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, email),
  UNIQUE (tenant_id, id)
);

CREATE TABLE identity.user_role (
  tenant_id uuid NOT NULL,
  user_id uuid NOT NULL,
  role varchar(20) NOT NULL CHECK (role IN ('CUSTOMER', 'AGENT', 'ADMIN')),
  PRIMARY KEY (tenant_id, user_id, role),
  FOREIGN KEY (tenant_id, user_id)
    REFERENCES identity.app_user(tenant_id, id) ON DELETE CASCADE
);

CREATE TABLE identity.refresh_session (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL,
  user_id uuid NOT NULL,
  family_id uuid NOT NULL,
  token_hash char(64) NOT NULL UNIQUE,
  replaced_by_id uuid,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_used_at timestamptz,
  FOREIGN KEY (tenant_id, user_id)
    REFERENCES identity.app_user(tenant_id, id)
);

CREATE INDEX ix_app_user_login ON identity.app_user(tenant_id, email);
CREATE INDEX ix_refresh_user ON identity.refresh_session(tenant_id, user_id, expires_at);
CREATE INDEX ix_refresh_family ON identity.refresh_session(tenant_id, family_id);
