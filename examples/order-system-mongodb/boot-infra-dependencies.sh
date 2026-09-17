#!/usr/bin/env bash
# Start infrastructure dependencies for local development.
# Assumes standalone helpers from the mercury-composable helpers/ directory.
# Adjust paths to match your local setup.

set -euo pipefail
HELPERS="$(cd "$(dirname "$0")/../.." && pwd)/helpers"

echo "=== Starting Kafka standalone ==="
java -jar "$HELPERS/kafka-standalone/target/kafka-standalone-4.11.9.jar" &
KAFKA_PID=$!
echo "Kafka PID: $KAFKA_PID"

echo "=== Starting Redis standalone ==="
java -jar "$HELPERS/redis-standalone/target/redis-standalone-4.11.9.jar" &
REDIS_PID=$!
echo "Redis PID: $REDIS_PID"

echo ""
echo "Waiting 10s for brokers to start..."
sleep 10

echo "=== Creating Kafka topics ==="
bash "$(dirname "$0")/setup-topics.sh"

echo "=== Setting up MongoDB databases ==="
mongosh --quiet setup-manager-db.js
mongosh --quiet setup-steward-db.js

echo ""
echo "Infrastructure ready."
echo "To stop: kill $KAFKA_PID $REDIS_PID"
