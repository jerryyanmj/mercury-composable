# Order Fulfillment System

Three independently deployable Mercury Composable applications, plus a System-of-Record mock,
implementing an order fulfillment pipeline: an order arrives over REST, is routed to a steward
that drives it through a four-checkpoint suspend/resume lifecycle against an external SoR, and
every transition is projected back into the manager's read model.

| Module | Ports | Owns | Role |
|---|---|---|---|
| `order-manager` | 8081 REST / 8091 Spring | `manager` DB | Order intake, CQRS read model, outbox relay |
| `order-steward` | 8082 REST / 8092 Spring | `steward` DB, Redis | Lifecycle graph, SoR dispatch, dispatch-window scheduler, outbox relay |
| `order-acknowledge` | — | (stateless) | `sor.ack` → `fulfillment.ack` |
| `order-sor-mock` | — | (stateless) | Stands in for the System of Record |

## Running it

Two layers of setup. Bring the infrastructure up once, then provision each project.

```shell
# 1. infrastructure: PostgreSQL, Redis, Kafka
./boot-infra-dependencies.sh

# 2. Kafka topics - the whole topology, every topic plus its dead-letter counterpart
node node/recreate-topics.mjs --all

# 3. per-project state: databases and the steward's Redis workflow keys
order-manager/boot-dependencies.sh
order-steward/boot-dependencies.sh

# 4. build and run
cd ../.. && mvn clean install -f examples/order-system/pom.xml -DskipTests
cd examples/order-system
for m in order-manager order-steward order-acknowledge order-sor-mock; do
  java -jar $m/target/$m-1.0.0.jar &
done
```

`boot-infra-dependencies.sh --status` reports what is up; `--stop` stops the Redis and Kafka
helpers (PostgreSQL is a shared Homebrew service and is left alone).

**Everything is deleted before it is created.** Databases are dropped and recreated from the DDL
in each project's `db/` folder, topics are deleted and recreated (which also drops their
consumer-group offsets), and the steward clears any leftover `graph:*` keys from Redis. Re-running
any of it always yields the same empty starting state.

**Topics live in one place.** `node/recreate-topics.mjs` holds the canonical list and derives every
`.dlq` from it, so there is one file to edit when the topology changes and no chance of two scripts
disagreeing about who owns a topic. `--all --if-missing` repairs a broker that lost some without
disturbing topics a running consumer is attached to.

Note that `kafka-standalone` wipes all topics on every restart by design, so
`boot-infra-dependencies.sh` always leaves you with an empty broker.

## Trying it

```shell
curl -X POST http://127.0.0.1:8081/api/v1/order -H 'Content-Type: application/json' \
  -d '{"order_id":"O-1001","external_party":"acme-corp","route":"steward-1",
       "payload":{"item":"widget","qty":3,"amount":500}}'
```

Within about a minute the lifecycle runs to completion. Watch it land:

```shell
psql -d steward -c "SELECT fulfillment_id, seq, from_state, to_state FROM fulfillment_event ORDER BY seq"
psql -d manager -c "SELECT * FROM order_view"
```

To exercise the failure path, add `"fail_milestone":"preprocessed"` to the payload — the SoR mock
will reject that milestone and the lifecycle terminates in `FAILED`.

## How an order arrives

Two ingresses, same tasks, different failure semantics.

**Kafka — the real path.** An external party publishes to `orders.inbound`; the manager consumes it
into the `order-intake-event` flow. Use the party mock to send one:

```shell
curl -X POST http://127.0.0.1:8084/api/v1/mock/order -H 'Content-Type: application/json' \
  -d '{"order_id":"O-1001","external_party":"acme","route":"steward-1",
       "payload":{"item":"widget","qty":3,"amount":500}}'
```

**REST — the convenience path.** `POST :8081/api/v1/order` runs the `order-intake` flow. Handy for
tests and for showing a synchronous response, but it is not how orders arrive in production.

The two flows exist separately for one reason: **what happens to a bad order.**

| | REST (`order-intake`) | Kafka (`order-intake-event`) |
|---|---|---|
| Bad order | flow's `exception:` handler answers **400** | **no handler** — the flow fails |
| Result | caller sees `{"message":"Missing route",...}` | retried, then `orders.inbound.dlq` |

That asymmetry is deliberate. Over Kafka nobody is waiting for an answer, so if a handler
*answered*, the flow would complete successfully, the adapter would commit the offset, and a
malformed order would be **silently dropped**. The absence of `exception:` in the event flow is the
feature — it lets the failure propagate to the dead-letter topic where it can be seen and replayed.

Try it: send an order whose `order_id` does not start with `O` and watch it land in
`orders.inbound.dlq` with nothing written to the database.

## Demo mode — driving the pipeline by hand

The pipeline normally advances on its own: two 15-second outbox relays, a 30-second dispatch
window, and a SoR mock that acknowledges all three milestones at once. That is right for
operation and useless for a demo, because an order goes from submitted to settled in under a
minute with nothing to look at in between.

Start the apps with `DEMO_MODE=true` and nothing advances until you ask it to:

```shell
for m in order-manager order-steward order-acknowledge order-sor-mock; do
  DEMO_MODE=true java -jar $m/target/$m-1.0.0.jar &
done
```

The scheduled jobs still tick but do nothing, and the SoR mock holds each dispatch instead of
acknowledging it. Every step is then an explicit call:

| Step | Call | Resulting state |
|---|---|---|
| 1 | `POST :8081/api/v1/order` | order stored; **steward knows nothing** |
| 2 | `POST :8081/api/v1/admin/relay` | `VALIDATED` → `SCHEDULED` |
| 3 | `POST :8082/api/v1/admin/dispatch` | `SCHEDULED` → `DISPATCHED` |
| 4 | `POST :8082/api/v1/admin/relay` | `sor.dispatch` published |
| 5 | `POST :8084/api/v1/sor/validated` | `DISPATCHED` → `SOR_VALIDATED` |
| 6 | `POST :8084/api/v1/sor/preprocessed` | `SOR_VALIDATED` → `SOR_PREPROCESSED` |
| 7 | `POST :8084/api/v1/sor/processed` | `SOR_PREPROCESSED` → `SETTLED` |
| 8 | `POST :8082/api/v1/admin/relay` | final `fulfillment.status` events published |
| 9 | `POST :8081/api/v1/admin/relay` | publishes `order.status.external` |
| 10 | `GET :8081/api/v1/order/{order_id}` | order + lines + full history |

The SoR steps take `{"fulfillment_id": "...", "outcome": "pass"}`; send `"outcome": "fail"` at any
of them to demo the failure path, and the reason recorded will name that stage.

Steps 2, 3, 4 and 8 are the ones worth pausing on — each is a checkpoint the graph suspends at,
with its state living in Redis (`graph:*`) and nothing running in between.

Step 9 publishes the external order-status notification. That event is staged in the manager's
outbox by the very transaction that concluded the order — the rollup's `UPDATE ... RETURNING`
feeds an `INSERT INTO outbox`, so a notification exists only when the status actually changed, and
a redelivered terminal event produces neither a version bump nor a duplicate announcement.

## Identifier conventions

Two rules hold everywhere in the system:

- **`order_id` starts with `O`** — supplied by the caller. A non-conforming id is **rejected with
  400**, never rewritten: the caller's identifier is what every downstream component correlates
  on, and an order is not modified to fit a convention.
- **`fulfillment_id` starts with `F`** — minted by the intake flow as `FUL-<order_id>-<8 hex>`.

Both are asserted at three boundaries: the intake guard (`v1.order.validate`), the manager's
persist step, and again in the steward when a `fulfillment.request` arrives — so a malformed id
dead-letters the record instead of starting a workflow.

## How it fits together

```
   external party ──orders.inbound──> order-manager
   (POST :8084/api/v1/mock/order)          |
                                           |
        order-manager ──fulfillment.request──> order-steward
              ^                                     |
              |                              sor.dispatch
        fulfillment.status                          v
              |                                order-sor-mock
              |                                     |
              |                                  sor.ack
              |                                     v
              └──── fulfillment.ack ──── order-acknowledge
```

The steward also publishes `fulfillment.tick` to itself: the dispatch-window scheduler decides
*which* fulfillments are due, but the lifecycle graph can only be driven from inside an Event
Script flow, so the tick rides Kafka into `steward-tick-flow` rather than being an HTTP call back
into the same JVM.

Both DB-owning apps use the transactional outbox pattern — a state change and the event announcing
it are written in one transaction, and a scheduled relay publishes unsent rows. There is no inbox
table; idempotency comes from natural keys (`line_history` is unique on `(fulfillment_id, seq)`)
and from Redis consume-on-read giving at-most-once resume.

## Lifecycle states

```
VALIDATED ─> SCHEDULED ─> DISPATCHED ─> SOR_VALIDATED ─> SOR_PREPROCESSED ─> SETTLED
                                              │                 │
                                              └────> FAILED <───┘
```

Four of these are suspension checkpoints: the graph persists its model to Redis and the run ends,
resuming only when the next event for that correlation id arrives.
