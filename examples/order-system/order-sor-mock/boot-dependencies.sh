#!/usr/bin/env bash
#
# boot-dependencies.sh - provision everything order-sor-mock owns, from scratch.
#
#   - the Kafka topics order-sor-mock CONSUMES, plus their dead-letter topics
#
# This project is stateless: it owns no database - it reads sor.dispatch and republishes to sor.ack,
# so Kafka is the whole of its state. Topics are deleted before they are created, which also
# drops their consumer-group offsets - that is what makes the script repeatable.
#
# A project only ever recreates the topics it consumes; the topic it publishes to is owned by
# the project that reads it, so no two boot scripts can delete each other's data.
#
# Requires the infrastructure to be up: ../boot-infra-dependencies.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYSTEM_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

TOPICS=(
  sor.dispatch
  sor.dispatch.dlq
)

log()   { printf '  %s\n' "$*"; }
head2() { printf '\n== %s ==\n' "$*"; }

if ! nc -z 127.0.0.1 9092 >/dev/null 2>&1; then
  echo "ERROR: Kafka is not running. Run ../boot-infra-dependencies.sh first." >&2
  exit 1
fi

head2 "kafka topics"
if [[ ! -d "$SYSTEM_DIR/node/node_modules" ]]; then
  log "installing the topic-admin helper (one time) ..."
  (cd "$SYSTEM_DIR/node" && npm install --silent)
fi
node "$SYSTEM_DIR/node/recreate-topics.mjs" "${TOPICS[@]}"

head2 "order-sor-mock is ready"
log "run with: java -jar target/order-sor-mock-1.0.0.jar"
