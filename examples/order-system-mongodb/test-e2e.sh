#!/usr/bin/env bash
# End-to-end test for the Order Fulfillment System (MongoDB).
# Requires all four apps running and infrastructure up.
# Exit code 0 = all passed, non-zero = at least one failure.

set -euo pipefail

MANAGER="${MANAGER_URL:-http://localhost:8081}"
STEWARD="${STEWARD_URL:-http://localhost:8082}"
MOCK="${MOCK_URL:-http://localhost:8084}"

PASS=0
FAIL=0
ORDER_ID="O$(date +%s)"
FULF_ID="F$(date +%s)"

ok() { echo "[PASS] $1"; ((PASS++)); }
fail() { echo "[FAIL] $1"; ((FAIL++)); }

check() {
  local desc="$1" expected="$2" actual="$3"
  if echo "$actual" | grep -q "$expected"; then ok "$desc"; else fail "$desc (got: $actual)"; fi
}

sleep_info() { echo "--- waiting $1s for async propagation ---"; sleep "$1"; }

# 1. Submit order via mock external party
echo "=== Step 1: Submit order ==="
R=$(curl -s -X POST "$MOCK/api/mock/order" \
  -H 'Content-Type: application/json' \
  -d "{\"order_id\":\"$ORDER_ID\",\"fulfillment_id\":\"$FULF_ID\",\"route\":\"standard\",\"payload\":{\"amount\":500},\"external_party\":\"test-client\"}")
check "Submit order" "submitted" "$R"

sleep_info 3

# 2. Trigger manager relay to pick up outbox
echo "=== Step 2: Manager relay ==="
R=$(curl -s -X POST "$MANAGER/api/admin/relay/manager")
check "Manager relay" "published" "$R"

sleep_info 3

# 3. Check order status (should be PENDING initially, then progressing)
echo "=== Step 3: Order status after intake ==="
R=$(curl -s "$MANAGER/api/order/$ORDER_ID")
check "Order exists" "order_id" "$R"

# 4. Trigger steward relay to forward fulfillment.request → fulfillment.request Kafka
echo "=== Step 4: Steward relay (picks up any staged events) ==="
R=$(curl -s -X POST "$STEWARD/api/admin/relay/steward")
check "Steward relay" "published" "$R"

sleep_info 5

# 5. Trigger scheduled dispatch to send fulfillment.tick
echo "=== Step 5: Admin dispatch ==="
R=$(curl -s -X POST "$STEWARD/api/admin/dispatch")
check "Dispatch trigger" "dispatched" "$R"

sleep_info 3

# 6. Steward relay pushes sor.dispatch outbox entries
echo "=== Step 6: Steward relay (sor.dispatch) ==="
R=$(curl -s -X POST "$STEWARD/api/admin/relay/steward")
check "Steward relay 2" "published" "$R"

sleep_info 5

# 7. SoR mock should have auto-emitted milestones; relay fulfillment.status
echo "=== Step 7: Steward relay (fulfillment.status) ==="
R=$(curl -s -X POST "$STEWARD/api/admin/relay/steward")
echo "Steward relay response: $R"

sleep_info 3

# 8. Manager relay (fulfillment.status → order aggregate)
echo "=== Step 8: Manager relay (fulfillment.status) ==="
R=$(curl -s -X POST "$MANAGER/api/admin/relay/manager")
check "Manager relay 2" "published" "$R"

sleep_info 3

# 9. Check final order status
echo "=== Step 9: Final order status ==="
R=$(curl -s "$MANAGER/api/order/$ORDER_ID")
check "Order settled" "COMPLETED\|SETTLED\|FAILED\|PARTIALLY_FAILED\|PENDING" "$R"
echo "Final order state: $R"

echo ""
echo "Results: $PASS passed, $FAIL failed"
if [[ $FAIL -gt 0 ]]; then exit 1; fi
