-- =====================================================================
-- manager database  (Aurora PostgreSQL)  -- the Order Manager service
-- Hybrid model: typed lifecycle spine + JSONB opaque payload.
-- Append-only history (read-model projection). Reference table, not ENUM.
-- Outbox present; NO inbox table (natural-key idempotency).
-- =====================================================================

-- ---- reference: order-level statuses (add a state = INSERT, not ALTER) ----
CREATE TABLE order_status_ref (
  status       text PRIMARY KEY,
  is_terminal  boolean NOT NULL DEFAULT false
);
INSERT INTO order_status_ref(status, is_terminal) VALUES
  ('RECEIVED', false), ('ROUTED', false),
  ('COMPLETED', true), ('PARTIALLY_FAILED', true), ('FAILED', true);

-- ---- write model: the order aggregate ----
CREATE TABLE "order" (
  order_id        text PRIMARY KEY,                                   -- business id = correlation id
  external_party  text NOT NULL,                                      -- who to notify
  payload         jsonb NOT NULL,                                     -- opaque order body
  order_status    text NOT NULL REFERENCES order_status_ref(status),  -- current aggregate status
  status_version  integer NOT NULL DEFAULT 0,                         -- monotonic; external dedupe key
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_order_status  ON "order"(order_status);
CREATE INDEX ix_order_party   ON "order"(external_party);
CREATE INDEX ix_order_payload ON "order" USING gin (payload);        -- ad-hoc queries into the body

-- ---- order -> fulfillment lines (1:1 today, 1:N ready = the bulk seam) ----
CREATE TABLE routing_map (
  order_id        text NOT NULL REFERENCES "order"(order_id),
  fulfillment_id  text NOT NULL,
  line_status     text,                                              -- current line status (from events)
  route           text,                                              -- target steward (constant today)
  PRIMARY KEY (order_id, fulfillment_id)
);
CREATE UNIQUE INDEX ux_routing_fulfillment ON routing_map(fulfillment_id);

-- ---- read-model projection: per-line status history (fed by fulfillment.status events) ----
CREATE TABLE line_history (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  fulfillment_id text NOT NULL,
  seq            integer NOT NULL,                                    -- event order + idempotent apply
  from_state     text,
  to_state       text NOT NULL,
  changed_at     timestamptz NOT NULL,
  reason         text,
  detail         jsonb NOT NULL DEFAULT '{}'
);
CREATE UNIQUE INDEX ux_line_history ON line_history(fulfillment_id, seq);  -- dup event = no-op

-- ---- outbox (drained by a mini-scheduler relay function; NO inbox table) ----
CREATE TABLE outbox (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  aggregate_id  text NOT NULL,                                        -- ordering key (order_id)
  topic         text NOT NULL,                                        -- fulfillment.request | order.status.external
  payload       jsonb NOT NULL,
  headers       jsonb NOT NULL DEFAULT '{}',
  created_at    timestamptz NOT NULL DEFAULT now(),
  sent_at       timestamptz
);
CREATE INDEX ix_outbox_unsent ON outbox(id) WHERE sent_at IS NULL;    -- relay: WHERE sent_at IS NULL

-- ---- reporting views (external report = order + lines + history) ----
CREATE VIEW order_view AS
  SELECT order_id, external_party, order_status, status_version, updated_at FROM "order";
CREATE VIEW line_view AS
  SELECT order_id, fulfillment_id, line_status FROM routing_map;
-- line_history above is the third leg of the report.

-- ---- service role: manager touches only the manager database ----
-- CREATE ROLE manager_svc LOGIN PASSWORD '***';
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO manager_svc;
