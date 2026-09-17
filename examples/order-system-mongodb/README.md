# Order Fulfillment System — MongoDB

A self-contained Mercury Composable implementation of an order fulfillment system using
MongoDB as the primary database, Redis for workflow state, and Kafka as the event bus.

This is a fresh design — not a port of `examples/order-system/` (which uses PostgreSQL).

---

## Architecture

```
External Party
     │ POST /api/mock/order (or orders.inbound Kafka)
     ▼
┌─────────────┐   fulfillment.request   ┌──────────────────┐
│  order-     │──────────────────────────▶  order-steward   │
│  manager    │◀────────────────────────── (lifecycle graph) │
│  (CQRS)     │   fulfillment.status    └──────────────────┘
└─────────────┘                                   │ sor.dispatch (outbox)
                                                  ▼
                                        ┌──────────────────┐
                                        │  order-external- │
                                        │  mock (SoR mock) │
                                        └──────────────────┘
                                          │ sor.validation
                                          │ sor.preprocess
                                          │ sor.process
                                          ▼
                                        ┌──────────────────┐
                                        │  order-          │
                                        │  acknowledge     │
                                        │  (anti-ACL)      │
                                        └──────────────────┘
                                          │ fulfillment.ack
                                          ▼
                                        order-steward (milestone accumulator)
```

### Four applications

| App | Port | Role |
|-----|------|------|
| `order-manager` | 8081 (mgmt: 8091) | CQRS read model, order intake, outbox relay |
| `order-steward` | 8082 (mgmt: 8092) | Lifecycle graph, SoR dispatch, milestone accumulation |
| `order-acknowledge` | (Kafka only) | Stateless SoR→fulfillment.ack translator |
| `order-external-mock` | 8084 (mgmt: 8094) | Mock SoR + test order submission |

### Two shared modules (libraries)

| Module | Purpose |
|--------|---------|
| `order-common-mongodb` | `Db`, `Events`, `Demo` helpers |
| `order-mongo-service` | `mongo.service` route (MongoDB reactive driver wrapper) |

---

## Prerequisites

- Java 21+, Maven 3.9+
- MongoDB (local or managed, default: `mongodb://localhost:27017`)
- Redis (local or `java -jar helpers/redis-standalone/target/*.jar`)
- Kafka (local or `java -jar helpers/kafka-standalone/target/*.jar`)

---

## Quick Start

### 1. Start infrastructure

```bash
# Option A: use Mercury's standalone helpers (no Docker required)
java -jar ../../helpers/kafka-standalone/target/kafka-standalone-4.11.9.jar &
java -jar ../../helpers/redis-standalone/target/redis-standalone-4.11.9.jar &

# Option B: use boot-infra-dependencies.sh (starts all helpers + setup)
bash boot-infra-dependencies.sh
```

### 2. Create Kafka topics

```bash
bash setup-topics.sh
```

### 3. Initialize MongoDB

```bash
mongosh setup-manager-db.js
mongosh setup-steward-db.js
```

### 4. Build

```bash
# from the mercury-composable root:
mvn clean install -DskipTests -pl examples/order-system-mongodb \
    --also-make-dependents
```

### 5. Start the apps (four terminals)

```bash
# Terminal 1 — order-manager
cd order-manager && java -jar target/order-manager-mongodb-1.0.0.jar

# Terminal 2 — order-steward
cd order-steward && java -jar target/order-steward-mongodb-1.0.0.jar

# Terminal 3 — order-acknowledge
cd order-acknowledge && java -jar target/order-acknowledge-mongodb-1.0.0.jar

# Terminal 4 — order-external-mock
cd order-external-mock && java -jar target/order-external-mock-1.0.0.jar
```

---

## Demo Walkthrough

### Automatic mode (default)

With `demo.mode=false` (the default), the SoR mock auto-emits all three milestones as
"pass" immediately after receiving a `sor.dispatch` event. The cron jobs fire every
15–30 seconds to relay outbox entries. A complete cycle takes ~1–2 minutes.

### Step-by-step demo mode

Set `demo.mode=true` in `order-steward/application.properties` and
`order-external-mock/application.properties` to suppress auto-emission and cron jobs.
Drive each step manually:

```bash
# 1. Submit an order
curl -X POST http://localhost:8084/api/mock/order \
  -H 'Content-Type: application/json' \
  -d '{"order_id":"O1001","fulfillment_id":"F1001","route":"standard","payload":{"amount":500},"external_party":"acme"}'

# 2. Manager relay: picks up fulfillment.request outbox entry and publishes to Kafka
curl -X POST http://localhost:8081/api/admin/relay/manager

# 3. Steward relay: not needed yet (no steward outbox entries)
#    (steward-request-flow runs automatically when Kafka delivers fulfillment.request)

# 4. Trigger dispatch (fulfillment is now SCHEDULED)
curl -X POST http://localhost:8082/api/admin/dispatch

# 5. Steward relay: publishes sor.dispatch outbox entry
curl -X POST http://localhost:8082/api/admin/relay/steward

# 6. Emit SoR milestones manually (DEMO mode)
curl -X POST http://localhost:8084/api/admin/sor/emit/SOR-F1001/validated/pass
curl -X POST http://localhost:8084/api/admin/sor/emit/SOR-F1001/preprocessed/pass
curl -X POST http://localhost:8084/api/admin/sor/emit/SOR-F1001/processed/pass

# 7. Steward relay: publishes fulfillment.status (SETTLED) outbox entry
curl -X POST http://localhost:8082/api/admin/relay/steward

# 8. Manager relay: picks up fulfillment.status, aggregates, stages order.status.external
curl -X POST http://localhost:8081/api/admin/relay/manager

# 9. Check final order status
curl http://localhost:8081/api/order/O1001
```

---

## Kafka Topics

| Topic | Producer | Consumer |
|-------|----------|----------|
| `orders.inbound` | external / mock | manager |
| `fulfillment.request` | manager (outbox) | steward |
| `fulfillment.tick` | steward (cron, direct) | steward |
| `sor.dispatch` | steward (outbox) | external-mock |
| `sor.validation` | external-mock | acknowledge |
| `sor.preprocess` | external-mock | acknowledge |
| `sor.process` | external-mock | acknowledge |
| `fulfillment.ack` | acknowledge (direct) | steward |
| `fulfillment.status` | steward (outbox) | manager |
| `order.status.external` | manager (outbox) | (external party) |

All topics have a corresponding `.dlq` dead-letter topic.

---

## Key Design Decisions

### Outbox pattern with single-document atomicity

State change + outbox entry land in **one `findOneAndUpdate` call** using MongoDB's
aggregation pipeline update (MongoDB 4.2+). No replica set or multi-document
transaction is required on the hot path.

### Milestones as an unordered set

The three SoR milestones (`validated`, `preprocessed`, `processed`) are stored as
a flat subdocument keyed by name. The first write wins (`$ifNull` in the pipeline
update); re-delivered milestones are idempotent no-ops. Terminal state is DERIVED
from the set — never from arrival order.

### Lifecycle graph (MiniGraph)

The steward uses the `fulfillment-lifecycle` Active Knowledge Graph (identical to the
PostgreSQL version). The graph stores intermediate state in Redis (`graph.suspend` /
`graph.resume`). MongoDB holds only the business state.

### Demo mode

`demo.mode=true` suppresses cron jobs and auto-SoR emission. Use admin REST endpoints
to drive the flow step by step — ideal for live demonstrations.

---

## Testing

```bash
# Run the end-to-end test (requires all apps running)
bash test-e2e.sh

# Import the Postman collection
postman/order-system-mongodb.postman_collection.json
```
