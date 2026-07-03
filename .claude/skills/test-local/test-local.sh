#!/usr/bin/env bash
# Validate a CloudStub service module through the real distribution path: publish it to mavenLocal,
# serve mavenLocal over HTTP, and have the standalone cloudstub-local server AUTO-DOWNLOAD the
# published module jar into an empty plugin directory, then confirm it loaded and serves.
#
# Usage: test-local.sh <serviceId> [--no-publish]
#   <serviceId>    the module to test, e.g. dynamodb, sqs, s3
#   --no-publish   skip publishToMavenLocal + checksum generation (reuse what is already in ~/.m2)
#
# The auto-downloader is HTTP-only (no file://), so we serve ~/.m2 with python3 -m http.server and
# point --maven-base-url at it. mavenLocal installs do not write checksums but the downloader
# requires one, so we generate .sha512/.sha256/.sha1 next to the published jar.
set -uo pipefail

SERVICE="${1:-}"
[[ -z "$SERVICE" ]] && { echo "usage: test-local.sh <serviceId> [--no-publish]"; exit 2; }
PUBLISH=true
[[ "${2:-}" == "--no-publish" ]] && PUBLISH=false

REPO="$(cd "$(dirname "$0")/../../.." && pwd)"
JAR="$REPO/cloudstub-local/build/libs/cloudstub-local.jar"
M2="$HOME/.m2/repository"
VERSION="$(grep -E '^version=' "$REPO/gradle.properties" | cut -d= -f2)"
ARTIFACT_DIR="$M2/io/github/cloudstub/cloudstub-$SERVICE/$VERSION"
MODULES_DIR="$(mktemp -d)/modules"; mkdir -p "$MODULES_DIR"   # empty -> forces auto-download
REPO_PORT=18080
PORT=15566
API_PORT=15567

cleanup() {
  [[ -n "${SERVER_PID:-}" ]] && kill "$SERVER_PID" 2>/dev/null
  [[ -n "${HTTP_PID:-}" ]] && kill "$HTTP_PID" 2>/dev/null
}
trap cleanup EXIT
fail() { echo "FAIL: $*"; echo "---- server log ----"; cat /tmp/test-local-server.log 2>/dev/null; exit 1; }
pass() { echo "PASS: $*"; }

echo "==> Service: cloudstub-$SERVICE   version: $VERSION"

if $PUBLISH; then
  echo "==> publishToMavenLocal (module + core)"
  ( cd "$REPO" && ./gradlew ":cloudstub-$SERVICE:publishToMavenLocal" :cloudstub-core:publishToMavenLocal -q ) \
    || fail "publishToMavenLocal failed — is cloudstub-$SERVICE in publishedServices + pomInfo? (see the skill)"
  echo "==> Generating checksums in mavenLocal (installs omit them; the downloader requires one)"
  JARF="$ARTIFACT_DIR/cloudstub-$SERVICE-$VERSION.jar"
  [[ -f "$JARF" ]] || fail "published jar not found at $JARF"
  shasum -a 512 "$JARF" | awk '{print $1}' > "$JARF.sha512"
  shasum -a 256 "$JARF" | awk '{print $1}' > "$JARF.sha256"
  shasum -a 1   "$JARF" | awk '{print $1}' > "$JARF.sha1"
fi

echo "==> Building standalone fat jar"
( cd "$REPO" && ./gradlew :cloudstub-local:shadowJar -q ) || fail "shadowJar failed"
[[ -f "$JAR" ]] || fail "fat jar not found at $JAR"

echo "==> Serving mavenLocal over http://localhost:$REPO_PORT"
( cd "$M2" && python3 -m http.server "$REPO_PORT" ) > /tmp/test-local-repo.log 2>&1 &
HTTP_PID=$!

echo "==> Starting standalone server (empty plugin dir -> must auto-download from mavenLocal)"
java -jar "$JAR" \
  --port="$PORT" --api-port="$API_PORT" \
  --services="$SERVICE" \
  --modules-dir="$MODULES_DIR" \
  --maven-base-url="http://localhost:$REPO_PORT" \
  > /tmp/test-local-server.log 2>&1 &
SERVER_PID=$!

for i in $(seq 1 40); do
  curl -sf "http://localhost:$API_PORT/api/status" > /dev/null 2>&1 && break
  [[ $i -eq 40 ]] && fail "server did not start within 20s"
  sleep 0.5
done
pass "server started"

ls "$MODULES_DIR" | grep -q "cloudstub-$SERVICE" \
  || fail "module jar was not auto-downloaded into the empty plugin dir"
pass "auto-downloaded from mavenLocal: $(ls "$MODULES_DIR")"

curl -sf "http://localhost:$API_PORT/api/status" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); assert '$SERVICE' in json.dumps(d).lower(), d" \
  || fail "$SERVICE not listed in /api/status"
pass "$SERVICE module loaded and serving"

echo ""
echo "==> Distribution path validated. Exercise the service's protocol + REST routes with:"
echo "    ./.claude/skills/run-cloudstub/smoke.sh --services=$SERVICE"
echo "==> All standalone + mavenLocal checks passed."
