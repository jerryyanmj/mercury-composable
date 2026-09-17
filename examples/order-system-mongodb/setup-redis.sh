#!/usr/bin/env bash
# Redis requires no schema setup. This script verifies connectivity.
set -euo pipefail

HOST="${REDIS_HOST:-localhost}"
PORT="${REDIS_PORT:-6379}"

if redis-cli -h "$HOST" -p "$PORT" ping | grep -q PONG; then
  echo "Redis at $HOST:$PORT is ready."
else
  echo "ERROR: Redis not reachable at $HOST:$PORT" >&2
  exit 1
fi
