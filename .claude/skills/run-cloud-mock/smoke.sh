#!/usr/bin/env bash
# Smoke test driver for CloudMock standalone server.
# Usage: ./smoke.sh [--port=N] [--api-port=N] [--modules=a,b] [--build]
# Starts the server, exercises each AWS protocol + all REST API routes, then stops it.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
JAR="$REPO_ROOT/cloudmock-standalone/build/libs/cloudmock-standalone.jar"
PORT=14566
API_PORT=14567
EXTRA_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --port=*)      PORT="${arg#--port=}" ;;
    --api-port=*)  API_PORT="${arg#--api-port=}" ;;
    --modules=*)   EXTRA_ARGS+=("$arg") ;;
    --build)
      echo "==> Building standalone JAR..."
      cd "$REPO_ROOT" && ./gradlew :cloudmock-standalone:shadowJar -q
      ;;
  esac
done

if [[ ! -f "$JAR" ]]; then
  echo "JAR not found: $JAR"
  echo "Run: ./gradlew :cloudmock-standalone:shadowJar"
  exit 1
fi

echo "==> Starting CloudMock on ports $PORT (mock) / $API_PORT (API)..."
java -jar "$JAR" --port="$PORT" --api-port="$API_PORT" ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"} \
  > /tmp/cloudmock-smoke.log 2>&1 &
SERVER_PID=$!

cleanup() {
  echo ""
  echo "==> Stopping server (PID $SERVER_PID)..."
  kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT

# Wait until /api/status responds (max 10s)
for i in $(seq 1 20); do
  if curl -sf "http://localhost:$API_PORT/api/status" > /dev/null 2>&1; then
    break
  fi
  if [[ $i -eq 20 ]]; then
    echo "ERROR: Server did not start within 10s. Log:"
    cat /tmp/cloudmock-smoke.log
    exit 1
  fi
  sleep 0.5
done
echo "    Server ready."

fail() { echo "FAIL: $*"; exit 1; }
pass() { echo "PASS: $*"; }

# ---- REST API routes --------------------------------------------------------

echo ""
echo "==> GET /api/status"
STATUS=$(curl -sf "http://localhost:$API_PORT/api/status")
echo "$STATUS" | python3 -m json.tool > /dev/null || fail "/api/status returned invalid JSON"
MODULE_COUNT=$(echo "$STATUS" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d['modules']))")
[[ "$MODULE_COUNT" -gt 0 ]] || fail "No modules loaded"
pass "/api/status  ($MODULE_COUNT modules)"

echo ""
echo "==> GET /api/openapi.json"
OAS=$(curl -sf "http://localhost:$API_PORT/api/openapi.json")
echo "$OAS" | python3 -c "import sys,json; d=json.load(sys.stdin); assert d['openapi']=='3.0.3'" || fail "/api/openapi.json bad schema"
pass "/api/openapi.json"

# ---- AWS mock protocols -----------------------------------------------------

echo ""
echo "==> SQS (JSON / X-Amz-Target)"
SQS=$(curl -sf -X POST "http://localhost:$PORT" \
  -H "X-Amz-Target: AmazonSQS.CreateQueue" \
  -H "Content-Type: application/x-amz-json-1.0" \
  -d '{"QueueName":"smoke-test"}')
echo "$SQS" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'QueueUrl' in d" || fail "SQS CreateQueue bad response: $SQS"
pass "SQS CreateQueue  → $SQS"

echo ""
echo "==> Secrets Manager (JSON / X-Amz-Target)"
SM=$(curl -sf -X POST "http://localhost:$PORT" \
  -H "X-Amz-Target: secretsmanager.CreateSecret" \
  -H "Content-Type: application/x-amz-json-1.1" \
  -d '{"Name":"smoke-secret","SecretString":"val"}')
echo "$SM" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'ARN' in d" || fail "Secrets Manager CreateSecret bad response: $SM"
pass "Secrets Manager CreateSecret  → $(echo "$SM" | python3 -c "import sys,json; print(json.load(sys.stdin)['ARN'])")"

echo ""
echo "==> SNS (XML / form-URL)"
SNS=$(curl -sf -X POST "http://localhost:$PORT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "Action=CreateTopic&Name=smoke-topic&Version=2010-03-31")
echo "$SNS" | grep -q "TopicArn" || fail "SNS CreateTopic bad response: $SNS"
pass "SNS CreateTopic"

echo ""
echo "==> GET /api/history (3 requests logged)"
HIST=$(curl -sf "http://localhost:$API_PORT/api/history")
COUNT=$(echo "$HIST" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['requests']))")
[[ "$COUNT" -ge 3 ]] || fail "Expected >=3 history entries, got $COUNT"
pass "/api/history  ($COUNT entries)"

echo ""
echo "==> POST /api/reset"
RESET=$(curl -sf -X POST "http://localhost:$API_PORT/api/reset")
echo "$RESET" | python3 -c "import sys,json; d=json.load(sys.stdin); assert d['status']=='ok'" || fail "/api/reset bad response"
pass "/api/reset"

HIST2=$(curl -sf "http://localhost:$API_PORT/api/history")
COUNT2=$(echo "$HIST2" | python3 -c "import sys,json; print(len(json.load(sys.stdin)['requests']))")
[[ "$COUNT2" -eq 0 ]] || fail "History not cleared after reset (still $COUNT2 entries)"
pass "/api/history empty after reset"

echo ""
echo "==> All checks passed."