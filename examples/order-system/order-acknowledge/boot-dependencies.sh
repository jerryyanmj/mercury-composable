#!/usr/bin/env bash
#
# boot-dependencies.sh - provision everything order-acknowledge owns, from scratch.
#
#   - the Kafka topics order-acknowledge CONSUMES, plus their dead-letter topics
#
# This project is stateless: it owns no database - it reads sor.ack and republishes to fulfillment.ack,
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

# The SoR publishes one topic per milestone - three inbound bindings, three DLQs.
TOPICS=(
  sor.validation
  sor.validation.dlq
  sor.preprocess
  sor.preprocess.dlq
  sor.process
  sor.process.dlq
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

head2 "order-acknowledge is ready"
log "run with: java -jar target/order-acknowledge-1.0.0.jar"
