-- =====================================================================
-- steward database  (Aurora PostgreSQL)  -- the Order Steward service
-- Hybrid model: typed lifecycle spine + JSONB opaque payload.
-- Authoritative append-only history (generic STATE + SOR_MILESTONE).
-- Suspend/resume checkpoint table (may be Redis instead).
-- Outbox present; NO inbox table (consume-on-read + guards).
-- =====================================================================

-- ---- reference: fulfillment lifecycle states ----
-- NOTE on SOR_VALIDATED / SOR_PREPROCESSED / SOR_PROCESSED: these are VESTIGIAL. They are listed
-- here, and deliberately not deleted, but no fulfillment ever enters them.
--
-- The three SoR milestones arrive on three independent topics with no ordering between them, so
-- they are a SET of facts rather than a sequence of states. Recording them as sequential states
-- invented an order that does not exist, which let a late failure be blamed on the wrong stage.
-- They are now stored in fulfillment_event as kind='SOR_MILESTONE' with a name and an outcome and
-- NO from_state/to_state (see ux_fev_milestone below, which makes them idempotent), and the
-- fulfillment stays DISPATCHED until the accumulated set decides SETTLED or FAILED.
--
-- An intermediate "2 of 3 milestones in" is a COUNT, not a state: three independent facts have
-- eight subsets, and a single-valued status column cannot name them. Progress is a query over
-- fulfillment_event, not a column.
--
-- They are kept rather than dropped because fulfillment.status carries a foreign key to this
-- table, historical rows may reference them, and this table is append-only by convention
-- ("add a state = INSERT, not ALTER"). The cost of three unused reference rows is nil; the cost
-- of deleting them is a data migration and a weakened audit trail.
CREATE TABLE fulfillment_status_ref (
  status       text PRIMARY KEY,
  is_terminal  boolean NOT NULL DEFAULT false
);
INSERT INTO fulfillment_status_ref(status, is_terminal) VALUES
  ('VALIDATED', false), ('REJECTED', true),
  ('SCHEDULED', false), ('DISPATCHED', false),
  ('SOR_VALIDATED', false), ('SOR_PREPROCESSED', false), ('SOR_PROCESSED', false),
  ('SETTLED', true), ('FAILED', true);

-- ---- the fulfillment aggregate ----
CREATE TABLE fulfillment (
  fulfillment_id text PRIMARY KEY,                                         -- business id = correlation id
  order_id       text NOT NULL,                                           -- REFERENCE value (no cross-db FK)
  payload        jsonb NOT NULL,                                          -- opaque fulfillment body (XML-derived work)
  status         text NOT NULL REFERENCES fulfillment_status_ref(status), -- current lifecycle state
  sor_reference  text,                                                    -- assigned at dispatch
  scheduled_at   timestamptz,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_ful_status  ON fulfillment(status);
CREATE INDEX ix_ful_order   ON fulfillment(order_id);
CREATE INDEX ix_ful_sorref  ON fulfillment(sor_reference);
CREATE INDEX ix_ful_payload ON fulfillment USING gin (payload);

-- ---- authoritative append-only history: internal STATE + SoR milestones, one shape ----
CREATE TABLE fulfillment_event (
  id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  fulfillment_id text NOT NULL REFERENCES fulfillment(fulfillment_id),
  seq            integer NOT NULL,
  kind           text NOT NULL,                                       -- 'STATE' | 'SOR_MILESTONE'
  name           text NOT NULL,                                       -- state name OR milestone name
  outcome        text,                                                -- PASS | FAIL (milestones)
  from_state     text,
  to_state       text,
  source         text NOT NULL,                                       -- STEWARD | SCHEDULER | SOR
  detail         jsonb NOT NULL DEFAULT '{}',
  occurred_at    timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_fev_seq       ON fulfillment_event(fulfillment_id, seq);
CREATE UNIQUE INDEX ux_fev_milestone ON fulfillment_event(fulfillment_id, name)
  WHERE kind = 'SOR_MILESTONE';                                       -- a milestone recorded at most once
CREATE INDEX        ix_fev_ful       ON fulfillment_event(fulfillment_id, occurred_at);

-- ---- suspend/resume checkpoint (consume-on-read). MAY live in Redis instead. ----
CREATE TABLE workflow_state (
  fulfillment_id text PRIMARY KEY,
  checkpoint     text NOT NULL,          -- SCHEDULED | AWAIT_VALIDATION | AWAIT_PREPROCESS | AWAIT_PROCESS
  model          jsonb NOT NULL,         -- persisted workflow model (minus reserved keys)
  run_seq        integer NOT NULL DEFAULT 0,
  expires_at     timestamptz NOT NULL    -- ttl = milestone SLA (timeout -> FAILED)
);

-- ---- outbox (drained by a mini-scheduler relay function; NO inbox table) ----
CREATE TABLE outbox (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  aggregate_id  text NOT NULL,           -- ordering key (fulfillment_id)
  topic         text NOT NULL,           -- sor.dispatch | fulfillment.status
  payload       jsonb NOT NULL,
  headers       jsonb NOT NULL DEFAULT '{}',
  created_at    timestamptz NOT NULL DEFAULT now(),
  sent_at       timestamptz
);
CREATE INDEX ix_outbox_unsent ON outbox(id) WHERE sent_at IS NULL;

-- ---- service role: steward touches only the steward database ----
-- CREATE ROLE steward_svc LOGIN PASSWORD '***';
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO steward_svc;
