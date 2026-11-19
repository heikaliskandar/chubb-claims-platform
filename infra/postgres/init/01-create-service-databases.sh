#!/bin/sh
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<EOSQL
CREATE USER claims_app WITH PASSWORD '$CLAIMS_DB_PASSWORD';
CREATE DATABASE claims OWNER claims_app;

CREATE USER insights_app WITH PASSWORD '$INSIGHTS_DB_PASSWORD';
CREATE DATABASE insights OWNER insights_app;
EOSQL
