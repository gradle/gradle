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

# Standard input (advertised via the build.stdin capability): pipe a line in and the readInput task
# echoes it, proving the client fed the build's stdin over gRPC while output streamed back.
STDIN_OUT="$(printf 'hi-from-stdin\n' | "$PY" "$PROTO_ROOT/client.py" --endpoint "$ENDPOINT" --project-dir "$SAMPLE" --stdin readInput 2>&1)"; STDIN_EC=$?
if [ "$STDIN_EC" -eq 0 ] && printf '%s' "$STDIN_OUT" | grep -qF 'read: hi-from-stdin'; then
    echo "PASS  B8 forward standard input"; PASS=$((PASS+1))
else
    echo "FAIL  B8 forward standard input (exit=$STDIN_EC)"; printf '%s\n' "$STDIN_OUT" | sed 's/^/      | /' | tail -8; FAIL=$((FAIL+1))
fi

# Structured operation events: subscribe to TASK and the bridge maps the Tooling API's typed task
# events onto the wire's operation tree (start/finish + outcome). And a failed build reports a
# structured failure tree, not just a message.
check "B9 task operation events"  0 "[task] :hello" -- hello --events
check "B10 structured failure tree" 1 "[failure]"    -- boom

# Problems channel (version-dependent): recent daemons surface deprecations through the Problems API,
# older ones do not - so this PASSes on a recent target and is SKIPPED (not failed) on an old one.
PROB_OUT="$("$PY" "$PROTO_ROOT/client.py" --endpoint "$ENDPOINT" --project-dir "$SAMPLE" deprecated --problems 2>&1)"
if printf '%s' "$PROB_OUT" | grep -qF '[problem]'; then
    echo "PASS  B11 problem events (Gradle $TARGET surfaces the Problems API)"; PASS=$((PASS+1))
else
    echo "SKIP  B11 problem events (Gradle $TARGET does not surface Problems API events; try TARGET_GRADLE=8.14.3)"
fi

echo
echo "=== $PASS passed, $FAIL failed (target Gradle $TARGET, no in-daemon gRPC server) ==="
[ "$FAIL" -eq 0 ]
