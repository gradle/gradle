#!/usr/bin/env bash
# Demonstrates the cross-version gRPC bridge: the SAME non-JVM client (prototype/client.py) drives an
# OLD Gradle version - one with no in-daemon gRPC server - by talking gRPC to this JVM bridge, which
# relays to the target daemon through the classic Tooling API.
#
# Usage: GRADLE_BIN=<dist>/bin/gradle PY=<venv>/python [TARGET_GRADLE=8.5] ./run-bridge-demo.sh
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
PROTO_ROOT="$(cd "$HERE/.." && pwd)"
PY="${PY:-python3}"
: "${GRADLE_BIN:?set GRADLE_BIN to a gradle launcher that can build the bridge}"
TARGET="${TARGET_GRADLE:-8.5}"
SAMPLE="$HERE/legacy-sample"

echo "=== building the bridge ==="
"$GRADLE_BIN" -p "$HERE" installDist -q --no-daemon || { echo "bridge build failed"; exit 1; }
BIN="$HERE/build/install/grpc-tapi-bridge/bin/grpc-tapi-bridge"

echo "=== starting the bridge (target: Gradle $TARGET) ==="
OUT="$HERE/.bridge.out"; ERR="$HERE/.bridge.err"; : > "$OUT"; : > "$ERR"
"$BIN" --gradle-version "$TARGET" --port 0 >"$OUT" 2>"$ERR" &
BPID=$!
trap 'kill $BPID 2>/dev/null' EXIT

ENDPOINT=""
for _ in $(seq 1 120); do
    ENDPOINT="$(grep -m1 '^BRIDGE_ENDPOINT ' "$OUT" 2>/dev/null | awk '{print $2}')"
    [ -n "$ENDPOINT" ] && break
    kill -0 "$BPID" 2>/dev/null || { echo "bridge exited early:"; cat "$ERR"; exit 1; }
    sleep 0.5
done
[ -z "$ENDPOINT" ] && { echo "bridge did not report an endpoint"; cat "$ERR"; exit 1; }
echo "bridge listening on $ENDPOINT"
echo

PASS=0; FAIL=0
check() { # desc exit sub -- args...
    local desc="$1" exp="$2" sub="$3"; shift 3; [ "$1" = "--" ] && shift
    local out ec
    out="$("$PY" "$PROTO_ROOT/client.py" --endpoint "$ENDPOINT" --project-dir "$SAMPLE" "$@" 2>&1)"; ec=$?
    if [ "$ec" -eq "$exp" ] && printf '%s' "$out" | grep -qF -- "$sub"; then
        echo "PASS  $desc"; PASS=$((PASS+1))
    else
        echo "FAIL  $desc (exit=$ec want=$exp, missing: '$sub')"; printf '%s\n' "$out" | sed 's/^/      | /' | tail -8; FAIL=$((FAIL+1))
    fi
}

echo "=== driving Gradle $TARGET over gRPC through the bridge ==="
check "B1 run a task (hello)"           0 "Hello from the bridged build!"          -- hello
check "B2 failing task (-> exit 1)"     1 "Intentional failure for the bridge demo" -- boom
check "B3 query build environment"      0 "gradle version: $TARGET"                 -- --query env
check "B4 cancel a running build"       1 "BUILD CANCELLED"                         -- sleeper --cancel-after 3
# Handshake: the bridge advertises a reduced capability set (no plugin models), so the client refuses
# a plugin-model query up front instead of failing mid-request - the same client, degrading gracefully.
check "B5 handshake (reduced capabilities)" 0 "capabilities: build.run, control.cancel, models.build_environment" -- --query env
check "B6 plugin model refused by capability" 3 "no 'models.plugin' capability" -- --query project
# Structured build config: the bridge starts a fresh daemon per request, so it honours the FULL set,
# including environment variables (which the in-daemon direct path cannot apply - see scenario 22).
check "B7 build config (system property + env var)" 0 "DEMO_ENV=bridged-env" -- printConfig --sys demo.sys=bridged-sys --env DEMO_ENV=bridged-env

echo
echo "=== $PASS passed, $FAIL failed (target Gradle $TARGET, no in-daemon gRPC server) ==="
[ "$FAIL" -eq 0 ]
