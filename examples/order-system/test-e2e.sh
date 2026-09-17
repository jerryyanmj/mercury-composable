#!/usr/bin/env bash
#
# test-e2e.sh - end-to-end test suite for the Order Fulfillment System.
#
#   ./test-e2e.sh            full run: reprovision, restart apps, assert everything
#   ./test-e2e.sh --no-boot  assume the system is already running and provisioned
#
# Requires the infrastructure to be up (../boot-infra-dependencies.sh) and the modules built.
# The suite reprovisions both databases, so it always starts from a known-empty state.
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1
export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"
LOG_DIR="${ORDER_SYSTEM_LOG_DIR:-/tmp/order-system}"
API="http://127.0.0.1:8081/api/v1/order"

PASS=0; FAIL=0; FAILED_NAMES=()

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m  %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); FAILED_NAMES+=("$1"); printf '  \033[31mFAIL\033[0m  %s\n     expected: %s\n     actual  : %s\n' "$1" "$2" "$3"; }
eq()   { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "$2" "$3"; fi; }
head2() { printf '\n\033[1m%s\033[0m\n' "$1"; }

mq()  { psql -d manager -t -A -c "$1" 2>/dev/null; }
sq()  { psql -d steward -t -A -c "$1" 2>/dev/null; }
submit() { curl -s -X POST "$API" -H 'Content-Type: application/json' -d "$1"; }
status_of() { curl -s -o /tmp/tsuite.json -w '%{http_code}' -X POST "$API" -H 'Content-Type: application/json' -d "$1"; }
msg_of() { python3 -c "import json;print(json.load(open('/tmp/tsuite.json')).get('message',''))" 2>/dev/null; }
topic_count() { node node/list-topics.mjs 2>/dev/null | awk -v t="$1" '$1==t{print $2}'; }
publish() {
  cat > node/.pub.mjs <<'EOF'
import { Kafka } from 'kafkajs';
const _w = process.emitWarning;
process.emitWarning = (warn, ...rest) => {
  const name = typeof warn === 'object' ? warn.name : rest[0];
  if (name !== 'TimeoutNegativeWarning') _w(warn, ...rest);
};
const [topic, cid, body] = process.argv.slice(2);
const p = new Kafka({ brokers: ['127.0.0.1:9092'], logLevel: 1 }).producer();
await p.connect(); await p.send({ topic, messages: [{ value: body, headers: { cid } }] }); await p.disconnect();
EOF
  (cd node && node .pub.mjs "$1" "$2" "$3"); rm -f node/.pub.mjs
}

# ---------------------------------------------------------------- boot

if [ "${1:-}" != "--no-boot" ]; then
  head2 "SETUP - reprovision and restart"
  pkill -9 -f "order-.*-1.0.0.jar" 2>/dev/null; sleep 3
  # Topics come from the one canonical list, not from the per-project scripts.
  node node/recreate-topics.mjs --all > "$LOG_DIR/topics.log" 2>&1 \
    && ok "all topics recreated" || bad "topic setup" "created" "see $LOG_DIR/topics.log"
  for m in order-manager order-steward order-acknowledge order-sor-mock; do
    (cd "$m" && ./boot-dependencies.sh) > "$LOG_DIR/boot-$m.log" 2>&1 || { echo "  provisioning $m FAILED"; exit 1; }
    nohup java -jar "$m/target/$m-1.0.0.jar" > "$LOG_DIR/$m.log" 2>&1 &
  done
  printf '  waiting for startup'; for i in $(seq 1 9); do sleep 5; printf '.'; done; echo
  for m in order-manager order-steward order-acknowledge order-sor-mock; do
    if grep -qE "FAILED TO START|Unable to start|Application run failed|Error starting ApplicationContext" "$LOG_DIR/$m.log"; then
      bad "$m started" "OK" "FAILED"
    else ok "$m started"; fi
  done
  eq "graph model compiled" "1" "$(grep -c 'Compiled graph fulfillment-lifecycle' "$LOG_DIR/order-steward.log")"
fi

# ---------------------------------------------------------------- A. intake contract

head2 "A. INTAKE CONTRACT (validation via the flow's exception handler)"
eq "A1 missing order_id -> 400" "400" "$(status_of '{"route":"r","payload":{"amount":1}}')"
eq "A1 message"                 "Missing order_id" "$(msg_of)"
eq "A2 missing route -> 400"    "400" "$(status_of '{"order_id":"OA2","payload":{"amount":1}}')"
eq "A2 message"                 "Missing route" "$(msg_of)"
eq "A3 missing payload -> 400"  "400" "$(status_of '{"order_id":"OA3","route":"r"}')"
eq "A3 message"                 "Missing payload" "$(msg_of)"
eq "A4 empty body -> 400"       "400" "$(status_of '{}')"
eq "A5 blank order_id -> 400"   "400" "$(status_of '{"order_id":"   ","route":"r","payload":{"amount":1}}')"
eq "A6 valid order -> 202"      "202" "$(status_of '{"order_id":"OA6","external_party":"acme","route":"steward-1","payload":{"item":"x","amount":10}}')"
FID=$(python3 -c "import json;print(json.load(open('/tmp/tsuite.json'))['fulfillment_id'])" 2>/dev/null)
if [[ "$FID" =~ ^FUL-OA6-[0-9a-f]{8}$ ]]; then ok "A7 id format FUL-<order>-<8hex> ($FID)"; else bad "A7 id format" "FUL-OA6-<8hex>" "$FID"; fi
if [[ "$FID" == F* ]]; then ok "A8 fulfillment_id starts with F ($FID)"; else bad "A8 fulfillment_id prefix" "F*" "$FID"; fi
eq "A9 order_id not starting with O -> 400" "400" "$(status_of '{"order_id":"X-BAD","route":"steward-1","payload":{"amount":1}}')"
if [[ "$(msg_of)" == *"must start with 'O'"* ]]; then ok "A9 message names the rule"; else bad "A9 message" "must start with 'O'" "$(msg_of)"; fi
eq "A10 lowercase o rejected" "400" "$(status_of '{"order_id":"oLOWER","route":"steward-1","payload":{"amount":1}}')"

# ---------------------------------------------------------------- K. kafka ingress

head2 "K. KAFKA INGRESS (orders.inbound - the real path)"
curl -s -X POST http://127.0.0.1:8084/api/v1/mock/order -H 'Content-Type: application/json' \
  -d '{"order_id":"OK1-KAFKA","external_party":"acme","route":"steward-1","payload":{"item":"k","amount":400}}' >/dev/null
DLQ_BEFORE=$(topic_count orders.inbound.dlq)
# a malformed order has no caller to answer: it must dead-letter, never be silently dropped
curl -s -X POST http://127.0.0.1:8084/api/v1/mock/order -H 'Content-Type: application/json' \
  -d '{"order_id":"BAD-NO-PREFIX","route":"steward-1","payload":{"amount":1}}' >/dev/null
sleep 20
eq "K1 valid order ingested over Kafka" "1" "$(mq "SELECT count(*) FROM \"order\" WHERE order_id='OK1-KAFKA'")"
eq "K2 fulfillment minted for it" "1" "$(mq "SELECT count(*) FROM routing_map WHERE order_id='OK1-KAFKA'")"
eq "K3 malformed order not persisted" "0" "$(mq "SELECT count(*) FROM \"order\" WHERE order_id='BAD-NO-PREFIX'")"
eq "K4 malformed order dead-lettered" "$((DLQ_BEFORE+1))" "$(topic_count orders.inbound.dlq)"

# ---------------------------------------------------------------- submit the lifecycle corpus

head2 "SUBMIT - lifecycle corpus"
submit '{"order_id":"OB-HAPPY","external_party":"acme","route":"steward-1","payload":{"item":"w","amount":500}}' >/dev/null
submit '{"order_id":"OD-ZERO","external_party":"acme","route":"steward-1","payload":{"item":"z","amount":0}}' >/dev/null
for i in 1 2; do
  submit "{\"order_id\":\"OC$i-VAL\",\"external_party\":\"acme\",\"route\":\"steward-1\",\"payload\":{\"item\":\"a\",\"amount\":100,\"fail_milestone\":\"validated\"}}" >/dev/null
  submit "{\"order_id\":\"OC$i-PRE\",\"external_party\":\"acme\",\"route\":\"steward-1\",\"payload\":{\"item\":\"b\",\"amount\":200,\"fail_milestone\":\"preprocessed\"}}" >/dev/null
  submit "{\"order_id\":\"OC$i-PROC\",\"external_party\":\"acme\",\"route\":\"steward-1\",\"payload\":{\"item\":\"c\",\"amount\":300,\"fail_milestone\":\"processed\"}}" >/dev/null
done
for i in 1 2 3 4 5; do
  submit "{\"order_id\":\"OG$i\",\"external_party\":\"acme\",\"route\":\"steward-1\",\"payload\":{\"item\":\"g\",\"amount\":$((i*10))}}" >/dev/null
done
echo "  14 orders in flight; waiting for the slowest lifecycle"
for i in $(seq 1 24); do sleep 5; printf '.'; done; echo

# ---------------------------------------------------------------- B. happy path

head2 "B. HAPPY PATH"
eq "B1 fulfillment SETTLED" "SETTLED" "$(sq "SELECT status FROM fulfillment f JOIN (SELECT 1) x ON true WHERE f.order_id='OB-HAPPY'")"
# The spine is only what the STEWARD causes. The three SoR milestones are unordered facts, not
# states, so they are recorded as kind='SOR_MILESTONE' and are not part of this sequence.
eq "B2 four-transition spine" "VALIDATED -> SCHEDULED -> DISPATCHED -> SETTLED" \
   "$(sq "SELECT string_agg(e.to_state,' -> ' ORDER BY e.seq) FROM fulfillment_event e JOIN fulfillment f USING (fulfillment_id) WHERE f.order_id='OB-HAPPY' AND e.kind='STATE'")"
eq "B3 order COMPLETED" "COMPLETED" "$(mq "SELECT order_status FROM \"order\" WHERE order_id='OB-HAPPY'")"
eq "B4 status_version = 2" "2" "$(mq "SELECT status_version FROM \"order\" WHERE order_id='OB-HAPPY'")"
eq "B5 line SETTLED" "SETTLED" "$(mq "SELECT line_status FROM routing_map WHERE order_id='OB-HAPPY'")"
eq "B6 line_history 4 rows (spine only)" "4" "$(mq "SELECT count(*) FROM line_history h JOIN routing_map r USING (fulfillment_id) WHERE r.order_id='OB-HAPPY'")"

# ---------------------------------------------------------------- C. SoR failures

head2 "C. SoR MILESTONE FAILURES (reason must never be mis-attributed)"
# Deterministic now: the outcome is derived from the accumulated set, so arrival order cannot
# change either the terminal state or the reason. There is no longer an out-of-sequence rejection.
for spec in "OC1-VAL:sor_validated" "OC2-VAL:sor_validated" "OC1-PRE:sor_preprocessed" "OC2-PRE:sor_preprocessed" "OC1-PROC:sor_processed" "OC2-PROC:sor_processed"; do
  oid="${spec%%:*}"; expect="${spec##*:}"
  st=$(sq "SELECT status FROM fulfillment WHERE order_id='$oid'")
  rsn=$(sq "SELECT e.detail::text FROM fulfillment_event e JOIN fulfillment f USING (fulfillment_id) WHERE f.order_id='$oid' ORDER BY e.seq DESC LIMIT 1")
  lr=$(mq "SELECT h.reason FROM line_history h JOIN routing_map r USING (fulfillment_id) WHERE r.order_id='$oid' ORDER BY h.seq DESC LIMIT 1")
  case "$st:$lr" in
    FAILED:"$expect") ok "$oid FAILED, reason $expect" ;;
    FAILED:*)         bad "$oid reason attribution" "$expect" "$lr" ;;
    *)                bad "$oid terminal state" "FAILED / $expect" "$st / $lr" ;;
  esac
done

# ---------------------------------------------------------------- D. business rejection

head2 "D. BUSINESS REJECTION (amount = 0)"
eq "D1 fulfillment REJECTED" "REJECTED" "$(sq "SELECT status FROM fulfillment WHERE order_id='OD-ZERO'")"
eq "D2 two transitions" "VALIDATED -> REJECTED" \
   "$(sq "SELECT string_agg(e.to_state,' -> ' ORDER BY e.seq) FROM fulfillment_event e JOIN fulfillment f USING (fulfillment_id) WHERE f.order_id='OD-ZERO'")"
eq "D3 never dispatched" "" "$(sq "SELECT COALESCE(sor_reference,'') FROM fulfillment WHERE order_id='OD-ZERO'")"
eq "D4 order FAILED" "FAILED" "$(mq "SELECT order_status FROM \"order\" WHERE order_id='OD-ZERO'")"

# ---------------------------------------------------------------- G. concurrency

head2 "G. CONCURRENCY (5 simultaneous orders)"
eq "G1 all five SETTLED" "5" "$(sq "SELECT count(*) FROM fulfillment WHERE order_id LIKE 'OG%' AND status='SETTLED'")"
eq "G2 all five COMPLETED" "5" "$(mq "SELECT count(*) FROM \"order\" WHERE order_id LIKE 'OG%' AND order_status='COMPLETED'")"
eq "G3 each has the 4-transition spine" "5" \
   "$(sq "SELECT count(*) FROM (SELECT f.order_id FROM fulfillment f JOIN fulfillment_event e USING (fulfillment_id) WHERE f.order_id LIKE 'OG%' AND e.kind='STATE' GROUP BY f.order_id HAVING count(*)=4) q")"

# Checked HERE, before sections E and H hand-inject events: one notification per terminal line
# holds for the natural flow only. A replayed terminal event legitimately produces another
# notification - delivery is at-least-once, which is what status_version lets a consumer dedupe.
NATURAL_TERMINAL=$(mq "SELECT count(*) FROM routing_map WHERE line_status IN ('SETTLED','FAILED','REJECTED')")
NATURAL_NOTIFS=$(topic_count order.status.external)
eq "G4 one notification per terminal line (natural flow)" "$NATURAL_TERMINAL" "$NATURAL_NOTIFS"

# ---------------------------------------------------------------- M. milestone set model

head2 "M. SoR MILESTONES AS AN UNORDERED SET"
# A SETTLED fulfillment needs all three passes. One that failed early has fewer - the SoR stops
# at the milestone that failed - and one rejected before dispatch has none at all.
eq "M1 every SETTLED fulfillment has all three milestones" "0" \
   "$(sq "SELECT count(*) FROM fulfillment f WHERE f.status='SETTLED' AND (SELECT count(*) FROM fulfillment_event e WHERE e.fulfillment_id=f.fulfillment_id AND e.kind='SOR_MILESTONE' AND e.outcome='pass') <> 3")"
eq "M1b a fulfillment rejected before dispatch has none" "0" \
   "$(sq "SELECT count(*) FROM fulfillment_event e JOIN fulfillment f USING (fulfillment_id) WHERE f.status='REJECTED' AND e.kind='SOR_MILESTONE'")"
# a milestone is a fact, not a transition - it has no from/to
eq "M2 milestones carry no from_state/to_state" "0" \
   "$(sq "SELECT count(*) FROM fulfillment_event WHERE kind='SOR_MILESTONE' AND (from_state IS NOT NULL OR to_state IS NOT NULL)")"
eq "M3 every milestone names a real one" "0" \
   "$(sq "SELECT count(*) FROM fulfillment_event WHERE kind='SOR_MILESTONE' AND name NOT IN ('validated','preprocessed','processed')")"
# the spine is steward-caused transitions only; no SOR_* state may appear in it
eq "M4 no SOR_* state in the spine" "0" \
   "$(sq "SELECT count(*) FROM fulfillment_event WHERE kind='STATE' AND to_state LIKE 'SOR_%'")"
eq "M5 no self-transitions recorded" "0" \
   "$(sq "SELECT count(*) FROM fulfillment_event WHERE kind='STATE' AND from_state = to_state")"
eq "M6 the manager never saw a SOR_* state" "0" \
   "$(mq "SELECT count(*) FROM line_history WHERE to_state LIKE 'SOR_%'")"
# replaying a milestone is absorbed by the partial unique index
M_FUL=$(sq "SELECT fulfillment_id FROM fulfillment WHERE status='SETTLED' LIMIT 1")
M_BEFORE=$(sq "SELECT count(*) FROM fulfillment_event WHERE fulfillment_id='$M_FUL'")
publish fulfillment.ack "$M_FUL" "{\"fulfillment_id\":\"$M_FUL\",\"milestone\":\"validated\",\"outcome\":\"pass\"}" >/dev/null
sleep 15
eq "M7 replayed milestone adds no row" "$M_BEFORE" "$(sq "SELECT count(*) FROM fulfillment_event WHERE fulfillment_id='$M_FUL'")"
eq "M8 vestigial SOR_* states are still in the reference table" "3" \
   "$(sq "SELECT count(*) FROM fulfillment_status_ref WHERE status LIKE 'SOR_%'")"

# ---------------------------------------------------------------- E. idempotency

head2 "E. IDEMPOTENCY"
SET_FUL=$(sq "SELECT fulfillment_id FROM fulfillment WHERE status='SETTLED' LIMIT 1")
B_EV=$(sq "SELECT count(*) FROM fulfillment_event"); B_LH=$(mq "SELECT count(*) FROM line_history")
B_EXT=$(topic_count order.status.external)
publish fulfillment.ack "$SET_FUL" "{\"fulfillment_id\":\"$SET_FUL\",\"milestone\":\"processed\",\"outcome\":\"pass\"}" >/dev/null
publish fulfillment.ack "$SET_FUL" "{\"fulfillment_id\":\"$SET_FUL\",\"milestone\":\"validated\",\"outcome\":\"pass\"}" >/dev/null
sleep 15
eq "E1 replayed ack adds no steward event" "$B_EV" "$(sq "SELECT count(*) FROM fulfillment_event")"
eq "E2 replayed ack adds no history row"   "$B_LH" "$(mq "SELECT count(*) FROM line_history")"
eq "E3 fulfillment still SETTLED" "SETTLED" "$(sq "SELECT status FROM fulfillment WHERE fulfillment_id='$SET_FUL'")"
eq "E4 no extra external notification" "$B_EXT" "$(topic_count order.status.external)"
# duplicate projection event (same natural key) must be absorbed by UNIQUE(fulfillment_id, seq)
DUP_ORDER=$(mq "SELECT order_id FROM routing_map WHERE fulfillment_id='$SET_FUL'")
publish fulfillment.status "$SET_FUL" "{\"fulfillment_id\":\"$SET_FUL\",\"order_id\":\"$DUP_ORDER\",\"seq\":3,\"from_state\":\"SCHEDULED\",\"to_state\":\"DISPATCHED\",\"source\":\"STEWARD\",\"occurred_at\":\"2026-01-01T00:00:00Z\"}" >/dev/null
sleep 15
eq "E5 duplicate projection is a no-op" "$B_LH" "$(mq "SELECT count(*) FROM line_history")"

# ---------------------------------------------------------------- H. multi-line rollup

head2 "H. MULTI-LINE ROLLUP + status_version semantics"
H_ORDER=OG1; H_FUL=$(mq "SELECT fulfillment_id FROM routing_map WHERE order_id='$H_ORDER'")
psql -q -d manager -c "INSERT INTO routing_map(order_id, fulfillment_id, line_status, route) VALUES ('$H_ORDER','FUL-H-EXTRA','RECEIVED','steward-1')" 2>/dev/null
V0=$(mq "SELECT status_version FROM \"order\" WHERE order_id='$H_ORDER'")
publish fulfillment.status "$H_FUL" "{\"fulfillment_id\":\"$H_FUL\",\"order_id\":\"$H_ORDER\",\"seq\":97,\"from_state\":\"SOR_PREPROCESSED\",\"to_state\":\"SETTLED\",\"source\":\"STEWARD\",\"occurred_at\":\"2026-01-01T00:00:00Z\"}" >/dev/null
sleep 15
eq "H1 sibling in flight -> status unchanged" "COMPLETED" "$(mq "SELECT order_status FROM \"order\" WHERE order_id='$H_ORDER'")"
eq "H2 sibling in flight -> version NOT bumped" "$V0" "$(mq "SELECT status_version FROM \"order\" WHERE order_id='$H_ORDER'")"
publish fulfillment.status FUL-H-EXTRA "{\"fulfillment_id\":\"FUL-H-EXTRA\",\"order_id\":\"$H_ORDER\",\"seq\":1,\"from_state\":\"DISPATCHED\",\"to_state\":\"FAILED\",\"source\":\"STEWARD\",\"occurred_at\":\"2026-01-01T00:00:01Z\",\"reason\":\"sor_process\"}" >/dev/null
sleep 15
eq "H3 mixed outcome -> PARTIALLY_FAILED" "PARTIALLY_FAILED" "$(mq "SELECT order_status FROM \"order\" WHERE order_id='$H_ORDER'")"
eq "H4 real change -> version bumped" "$((V0+1))" "$(mq "SELECT status_version FROM \"order\" WHERE order_id='$H_ORDER'")"

# ---------------------------------------------------------------- F. integrity

head2 "F. INTEGRITY"
# The relays run on a 15s cron; section H staged rows moments ago, so let them drain first.
printf '  waiting for the relays to drain'; for i in 1 2 3 4 5; do sleep 5; printf '.'; done; echo
eq "F1 manager outbox drained" "0" "$(mq "SELECT count(*) FROM outbox WHERE sent_at IS NULL")"
eq "F2 steward outbox drained" "0" "$(sq "SELECT count(*) FROM outbox WHERE sent_at IS NULL")"
# exactly one, from K4's intentionally malformed order
eq "F3 only the intended dead letter" "1" "$(grep -h 'routing to.*dlq' "$LOG_DIR"/*.log 2>/dev/null | wc -l | tr -d ' ')"
DLQ_TOTAL=$(node node/list-topics.mjs 2>/dev/null | awk '/\.dlq/{s+=$2} END{print s+0}')
eq "F4 no unexpected dead letters" "1" "$DLQ_TOTAL"
eq "F5 every consumer lag is zero" "0" "$(node node/list-topics.mjs 2>/dev/null | grep -c 'pending')"
# The payload is built by the CONCLUDE_ORDER statement, so these assert the shape it produces -
# not the old direct-publish shape, whose field names no longer exist (an assertion looking for
# the old name would pass while testing nothing).
NOTIFS=$(node node/read-topic.mjs order.status.external 2>/dev/null)
eq "F6 every notification carries a version" "0" "$(printf '%s' "$NOTIFS" | grep -c '"version": null')"
eq "F11 every notification carries its line breakdown" "0" "$(printf '%s' "$NOTIFS" | grep -c '"lines": null')"
# Raw record count would be wrong: a re-concluded order (section H) legitimately announces again.
# What must hold is that every concluded order was announced at least once.
eq "F12 every concluded order was announced" \
   "$(mq "SELECT count(*) FROM \"order\" WHERE order_status IN ('COMPLETED','PARTIALLY_FAILED','FAILED')")" \
   "$(printf '%s' "$NOTIFS" | grep -oE '"order_id": "[^"]*"' | sort -u | wc -l | tr -d ' ')"
# every external notification must have been STAGED in the outbox, never published directly
eq "F13 external notifications went through the outbox" \
   "$(printf '%s' "$NOTIFS" | grep -c '"order_id"')" \
   "$(mq "SELECT count(*) FROM outbox WHERE topic='order.status.external'")"
eq "F14 all of them were relayed" "0" \
   "$(mq "SELECT count(*) FROM outbox WHERE topic='order.status.external' AND sent_at IS NULL")"
# These logs are pretty-printed JSON, so "level" and "message" are on different lines - a
# line-oriented grep -v cannot exclude a known-benign error. Parse the objects instead.
eq "F8 every order_id starts with O" "0" "$(mq "SELECT count(*) FROM \"order\" WHERE order_id NOT LIKE 'O%'")"
eq "F9 every fulfillment_id starts with F" "0" "$(sq "SELECT count(*) FROM fulfillment WHERE fulfillment_id NOT LIKE 'F%'")"
eq "F10 every routing line id starts with F" "0" "$(mq "SELECT count(*) FROM routing_map WHERE fulfillment_id NOT LIKE 'F%'")"
eq "F7 no unexpected app errors" "0" "$(python3 - "$LOG_DIR" <<'PYEOF'
import json, sys, glob
benign = ("MacOSDnsServerAddressStreamProvider",)
count = 0
for path in glob.glob(sys.argv[1] + "/order-*.log"):
    raw = open(path, errors="replace").read()
    dec, i = json.JSONDecoder(), 0
    while i < len(raw):
        while i < len(raw) and raw[i] in " \n\r\t": i += 1
        if i >= len(raw): break
        try:
            o, i = dec.raw_decode(raw, i)
        except Exception:
            nl = raw.find("\n", i); i = nl + 1 if nl >= 0 else len(raw); continue
        if isinstance(o, dict) and o.get("level") == "ERROR":
            msg = str(o.get("message", ""))
            if not any(b in msg for b in benign): count += 1
print(count)
PYEOF
)"

# ---------------------------------------------------------------- summary

head2 "SUMMARY"
printf '  passed: %d\n  failed: %d\n' "$PASS" "$FAIL"
if [ "$FAIL" -gt 0 ]; then printf '\n  failures:\n'; for n in "${FAILED_NAMES[@]}"; do printf '    - %s\n' "$n"; done; exit 1; fi
printf '\n  ALL ASSERTIONS PASSED\n'
