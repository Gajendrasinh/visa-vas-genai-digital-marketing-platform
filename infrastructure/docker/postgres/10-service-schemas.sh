#!/usr/bin/env bash
# Creates one schema and one login role per service (ADR-015). A role owns only its schema and has
# no USAGE on any other, so cross-service reads fail in the database, not just by convention.
set -euo pipefail

: "${SERVICE_DB_PASSWORD:?SERVICE_DB_PASSWORD must be set}"

SCHEMAS=(customer segment offer campaign personalization notification analytics audit aigw orchestrator rag evaluation)

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
  REVOKE ALL ON SCHEMA public FROM PUBLIC;
  REVOKE ALL ON DATABASE "$POSTGRES_DB" FROM PUBLIC;
SQL

for schema in "${SCHEMAS[@]}"; do
  role="svc_${schema}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
       -v role="$role" -v schema="$schema" -v pw="$SERVICE_DB_PASSWORD" -v db="$POSTGRES_DB" <<-'SQL'
    CREATE ROLE :"role" LOGIN PASSWORD :'pw' CONNECTION LIMIT 20;
    GRANT CONNECT ON DATABASE :"db" TO :"role";
    CREATE SCHEMA :"schema" AUTHORIZATION :"role";
    ALTER ROLE :"role" SET search_path = :"schema";
SQL
done
echo "Created ${#SCHEMAS[@]} service schemas"
