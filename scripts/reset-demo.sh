#!/usr/bin/env bash
# Wipes Postgres and Redis, recreates the schema, loads the demo seed.
# Run from the repo root. Restart Spring afterwards.
set -euo pipefail
cd "$(dirname "$0")/.."
POSTGRES_USER=${POSTGRES_USER:-obsidian}
POSTGRES_DB=${POSTGRES_DB:-obsidian}

docker compose down -v
docker compose up -d --wait
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" < scripts/seed.sql
echo "Demo data loaded. Accounts: avery.lin@aster.example (admin), priya.nair@aster.example (employee),"
echo "riley.chen@mail.example (consumer); password obsidian-demo-2026. Now run scripts/prewarm-vt.sh."
