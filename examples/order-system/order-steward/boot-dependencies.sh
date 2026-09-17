#!/usr/bin/env bash
#
# boot-dependencies.sh - provision everything order-steward owns, from scratch.
#
#   - the `steward` PostgreSQL database (dropped and recreated from db/steward.sql)
#   - the Redis suspend/resume state (any leftover graph:* keys are deleted)
#
# Everything is deleted before it is created, so the script is repeatable and always leaves a
# known-empty starting state.
#
# Kafka topics are NOT created here. The whole topology lives in one place:
#     node ../node/recreate-topics.mjs --all
#
# Clearing Redis matters as much as clearing the database: a graph:* key is a suspended
# workflow. Leaving one behind means a fresh run can resume into a lifecycle whose database
# rows no longer exist.
#
# Requires the infrastructure to be up: ../boot-infra-dependencies.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DB_NAME="${STEWARD_DB:-steward}"
PG_FORMULA="${PG_FORMULA:-postgresql@16}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"

log()   { printf '  %s\n' "$*"; }
head2() { printf '\n== %s ==\n' "$*"; }

command -v psql >/dev/null 2>&1 || export PATH="/opt/homebrew/opt/$PG_FORMULA/bin:$PATH"

if ! nc -z 127.0.0.1 5432 >/dev/null 2>&1; then
  echo "ERROR: PostgreSQL is not running. Run ../boot-infra-dependencies.sh first." >&2
  exit 1
fi
if ! nc -z "$REDIS_HOST" "$REDIS_PORT" >/dev/null 2>&1; then
  echo "ERROR: Redis is not running. Run ../boot-infra-dependencies.sh first." >&2
  exit 1
fi

# ---------------------------------------------------------------- database

head2 "database: $DB_NAME"
# --force terminates any other session still attached (a psql shell, an open GUI client).
# Without it a single idle connection defeats the whole point of a repeatable script.
dropdb --if-exists --force "$DB_NAME"
log "dropped $DB_NAME (if it existed)"
createdb "$DB_NAME"
psql -q -d "$DB_NAME" -f "$SCRIPT_DIR/db/steward.sql"
log "created $DB_NAME and applied db/steward.sql"
psql -d "$DB_NAME" -t -A -c \
  "SELECT '  table: '||tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename"

# ---------------------------------------------------------------- redis workflow state

head2 "redis suspend/resume state"
if command -v redis-cli >/dev/null 2>&1; then
  # Scoped delete rather than FLUSHALL - this Redis may be shared with other local work.
  removed="$(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" --no-raw EVAL \
    "local k = redis.call('KEYS', ARGV[1]); for i=1,#k do redis.call('DEL', k[i]) end; return #k" \
    0 'graph:*' 2>/dev/null || echo '?')"
  log "deleted ${removed//[^0-9]/} suspended workflow key(s) matching graph:*"
else
  log "NOTE: redis-cli not found - could not clear graph:* keys."
  log "      A leftover suspended workflow may resume against a database that no longer has it."
fi

#head2 "order-steward is ready"
#log "topics: node ../node/recreate-topics.mjs --all"
#log "run with: java -jar target/order-steward-1.0.0.jar   (graph REST on :8082)"
