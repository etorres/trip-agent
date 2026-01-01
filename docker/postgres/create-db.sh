#!/usr/bin/env bash

set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER trip_agent WITH PASSWORD 'changeMe';
    CREATE DATABASE trip_searches;
    GRANT ALL PRIVILEGES ON DATABASE trip_searches TO trip_agent;
    \connect trip_searches "$POSTGRES_USER"
    GRANT USAGE, CREATE ON SCHEMA public TO trip_agent;
EOSQL