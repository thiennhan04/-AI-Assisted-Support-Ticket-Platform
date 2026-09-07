#!/bin/sh
set -eu

create_service_database() {
  database_name="$1"
  database_user="$2"
  database_password="$3"

  psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 \
    --set=database_name="$database_name" \
    --set=database_user="$database_user" \
    --set=database_password="$database_password" <<-'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'database_user', :'database_password')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = :'database_user')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'database_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database_name')
\gexec
SQL
}

create_service_database identity_db "$IDENTITY_DB_USER" "$IDENTITY_DB_PASSWORD"
create_service_database ticket_db "$TICKET_DB_USER" "$TICKET_DB_PASSWORD"
create_service_database ai_db "$AI_DB_USER" "$AI_DB_PASSWORD"
create_service_database knowledge_db "$KNOWLEDGE_DB_USER" "$KNOWLEDGE_DB_PASSWORD"

psql --username "$POSTGRES_USER" --dbname knowledge_db --set=ON_ERROR_STOP=1 \
  --command='CREATE EXTENSION IF NOT EXISTS vector;'
