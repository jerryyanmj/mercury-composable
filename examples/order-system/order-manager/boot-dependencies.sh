#!/usr/bin/env bash
#
# boot-dependencies.sh - provision everything order-manager owns, from scratch.
#
#   - the `manager` PostgreSQL database (dropped and recreated from db/manager.sql)
#
# Everything is deleted before it is created, so the script is repeatable and always leaves a
# known-empty starting state.
#
# Kafka topics are NOT created here. The whole topology lives in one place:
#     node ../node/recreate-topics.mjs --all
#
# Requires the infrastructure to be up: ../boot-infra-dependencies.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DB_NAME="${MANAGER_DB:-manager}"
DB_USER="${PGUSER:-$(whoami)}"
PG_FORMULA="${PG_FORMULA:-postgresql@16}"

log()   { printf '  %s\n' "$*"; }
head2() { printf '\n== %s ==\n' "$*"; }

command -v psql >/dev/null 2>&1 || export PATH="/opt/homebrew/opt/$PG_FORMULA/bin:$PATH"

if ! nc -z 127.0.0.1 5432 >/dev/null 2>&1; then
  echo "ERROR: PostgreSQL is not running. Run ../boot-infra-dependencies.sh first." >&2
  exit 1
fi

# ---------------------------------------------------------------- database

head2 "database: $DB_NAME"
# --force terminates any other session still attached (a psql shell, an open GUI client).
# Without it a single idle connection defeats the whole point of a repeatable script.
dropdb --if-exists --force "$DB_NAME"
log "dropped $DB_NAME (if it existed)"
createdb "$DB_NAME"
psql -q -d "$DB_NAME" -f "$SCRIPT_DIR/db/manager.sql"
log "created $DB_NAME and applied db/manager.sql"
psql -d "$DB_NAME" -t -A -c \
  "SELECT '  table: '||tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename"

#head2 "order-manager is ready"
#log "topics: node ../node/recreate-topics.mjs --all"
#log "run with: java -jar target/order-manager-1.0.0.jar   (REST on :8081)"
