#!/usr/bin/env bash
#
# boot-infra-dependencies.sh - bring up the three infrastructure servers the Order Fulfillment
# System runs against: PostgreSQL, Redis and Kafka.
#
#   ./boot-infra-dependencies.sh          start everything (restarts what is already running)
#   ./boot-infra-dependencies.sh --status report what is up, change nothing
#   ./boot-infra-dependencies.sh --stop    stop the helpers this script starts
#
# Redis and Kafka run from helpers/ as plain java processes - no Docker. PostgreSQL is a Homebrew
# service, so it is started but never wiped here: the databases are owned by each project's own
# boot-dependencies.sh, which drops and recreates them.
#
# Note that kafka-standalone deletes all topics on every restart, by design. That is why this
# script always restarts it: infrastructure comes up empty, and the per-project scripts then
# create exactly the topics their app needs.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOG_DIR="${ORDER_SYSTEM_LOG_DIR:-/tmp/order-system}"

PG_FORMULA="${PG_FORMULA:-postgresql@16}"
PG_PORT=5432
REDIS_PORT=6379
KAFKA_PORT=9092

log()  { printf '  %s\n' "$*"; }
head2() { printf '\n== %s ==\n' "$*"; }

port_up() { nc -z 127.0.0.1 "$1" >/dev/null 2>&1; }

wait_for_port() {
  local port=$1 name=$2 tries=${3:-60}
  for ((i = 0; i < tries; i++)); do
    if port_up "$port"; then
      log "$name is accepting connections on $port"
      return 0
    fi
    sleep 1
  done
  log "ERROR: $name did not come up on port $port"
  return 1
}

# The helper jars are versioned with the framework; read the version from the root pom rather
# than pinning it here, so this script survives a release bump. (Kept to grep/sed that BSD and
# GNU agree on - `sed -n '0,/re/'` is a GNU extension and silently yields nothing on macOS.)
mercury_version() {
  grep -m1 -o '<version>[^<]*</version>' "$REPO_ROOT/pom.xml" | sed 's|</\{0,1\}version>||g'
}

# `|| true` matters: pgrep exits 1 when nothing matches, and under `set -o pipefail` that would
# abort the script on the perfectly normal "helper is not running yet" case.
helper_pid() { pgrep -f "$1" 2>/dev/null | head -1 || true; }

# The embedded redis-server is a CHILD process of the java wrapper and does NOT die with it.
# An orphan left behind keeps port 6379, so the next start silently fails while the stale server
# answers - and its temp working directory may since have been cleaned, leaving it unable to
# bgsave, which makes Redis disable writes entirely (MISCONF).
stop_orphan_redis_server() {
  if pgrep -f "redis-server-.*-darwin" >/dev/null 2>&1 || pgrep -f "redis-server \*:" >/dev/null 2>&1; then
    pkill -9 -f "redis-server-.*-darwin" 2>/dev/null || true
    pkill -9 -f "redis-server \*:" 2>/dev/null || true
    log "killed an orphaned redis-server child"
    sleep 1
  fi
}

stop_helper() {
  local pattern=$1 name=$2 pid
  pid="$(helper_pid "$pattern")"
  if [[ -n "$pid" ]]; then
    kill "$pid" 2>/dev/null || true
    for ((i = 0; i < 15; i++)); do
      kill -0 "$pid" 2>/dev/null || break
      sleep 1
    done
    kill -9 "$pid" 2>/dev/null || true
    log "stopped $name (pid $pid)"
  else
    log "$name was not running"
  fi
}

# ---------------------------------------------------------------- status / stop

if [[ "${1:-}" == "--status" ]]; then
  head2 "infrastructure status"
  for spec in "PostgreSQL:$PG_PORT" "Redis:$REDIS_PORT" "Kafka:$KAFKA_PORT"; do
    name="${spec%%:*}"; port="${spec##*:}"
    if port_up "$port"; then log "$name  UP    (port $port)"; else log "$name  DOWN  (port $port)"; fi
  done
  exit 0
fi

if [[ "${1:-}" == "--stop" ]]; then
  head2 "stopping helpers"
  stop_helper "kafka-standalone-.*-exec.jar" "kafka-standalone"
  stop_helper "redis-standalone-.*\.jar" "redis-standalone"
  stop_orphan_redis_server
  log "PostgreSQL left running (a shared Homebrew service - stop it with: brew services stop $PG_FORMULA)"
  exit 0
fi

VERSION="$(mercury_version)"
mkdir -p "$LOG_DIR"

# ---------------------------------------------------------------- PostgreSQL

head2 "PostgreSQL ($PG_FORMULA)"
if port_up "$PG_PORT"; then
  log "already running on $PG_PORT"
else
  if command -v brew >/dev/null 2>&1; then
    brew services start "$PG_FORMULA" >/dev/null
    wait_for_port "$PG_PORT" "PostgreSQL"
  else
    log "ERROR: PostgreSQL is not running and Homebrew was not found."
    log "Start it yourself, then re-run this script."
    exit 1
  fi
fi
if ! command -v psql >/dev/null 2>&1; then
  log "NOTE: psql is not on PATH. The per-project scripts need it:"
  log "      export PATH=/opt/homebrew/opt/$PG_FORMULA/bin:\$PATH"
fi

# An open port proves something is listening, not that it works. Redis with a failed bgsave
# answers PING but refuses every write, which strands the graph engine's suspend/resume.
verify_redis_writable() {
  command -v redis-cli >/dev/null 2>&1 || { log "redis-cli absent - skipping the write check"; return 0; }
  local probe
  probe="$(redis-cli -h 127.0.0.1 -p "$REDIS_PORT" set __boot_probe__ ok 2>&1 || true)"
  if [[ "$probe" != *OK* ]]; then
    log "ERROR: Redis is listening but NOT writable: $probe"
    return 1
  fi
  redis-cli -h 127.0.0.1 -p "$REDIS_PORT" del __boot_probe__ >/dev/null 2>&1 || true
  log "Redis accepted a write"
}

# ---------------------------------------------------------------- Redis

head2 "redis-standalone"
REDIS_JAR="$REPO_ROOT/helpers/redis-standalone/target/redis-standalone-$VERSION.jar"
if [[ ! -f "$REDIS_JAR" ]]; then
  log "building redis-standalone ..."
  (cd "$REPO_ROOT" && mvn -q clean package -DskipTests -f helpers/redis-standalone/pom.xml)
fi
stop_helper "redis-standalone-.*\.jar" "redis-standalone"
stop_orphan_redis_server
nohup java -jar "$REDIS_JAR" > "$LOG_DIR/redis-standalone.log" 2>&1 &
wait_for_port "$REDIS_PORT" "Redis"
verify_redis_writable
log "log: $LOG_DIR/redis-standalone.log"

# ---------------------------------------------------------------- Kafka

head2 "kafka-standalone"
KAFKA_JAR="$REPO_ROOT/helpers/kafka-standalone/target/kafka-standalone-$VERSION-exec.jar"
if [[ ! -f "$KAFKA_JAR" ]]; then
  log "building kafka-standalone ..."
  (cd "$REPO_ROOT" && mvn -q clean package -DskipTests -f helpers/kafka-standalone/pom.xml)
fi
stop_helper "kafka-standalone-.*-exec.jar" "kafka-standalone"
nohup java -jar "$KAFKA_JAR" > "$LOG_DIR/kafka-standalone.log" 2>&1 &
wait_for_port "$KAFKA_PORT" "Kafka" 90
log "log: $LOG_DIR/kafka-standalone.log"
log "all topics were wiped by the restart - run each project's boot-dependencies.sh next"

# ---------------------------------------------------------------- done

head2 "ready"
log "next: order-manager/boot-dependencies.sh, order-steward/boot-dependencies.sh,"
log "      order-acknowledge/boot-dependencies.sh, order-sor-mock/boot-dependencies.sh"
