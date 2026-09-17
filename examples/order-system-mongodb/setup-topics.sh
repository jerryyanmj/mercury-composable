#!/usr/bin/env bash
# Create Kafka topics for the Order Fulfillment System (MongoDB).
# Run once after the Kafka broker is ready.

set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:9092}"

create() {
  local topic="$1"
  kafka-topics.sh --bootstrap-server "$BOOTSTRAP" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions 3 \
    --replication-factor 1
}

# Business topics
create orders.inbound
create fulfillment.request
create fulfillment.tick
create fulfillment.ack
create fulfillment.status
create sor.dispatch
create sor.validation
create sor.preprocess
create sor.process
create order.status.external

# DLQ topics
create orders.inbound.dlq
create fulfillment.request.dlq
create fulfillment.tick.dlq
create fulfillment.ack.dlq
create fulfillment.status.dlq
create sor.dispatch.dlq
create sor.validation.dlq
create sor.preprocess.dlq
create sor.process.dlq
create order.status.external.dlq

echo "All topics created."
