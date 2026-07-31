#!/usr/bin/env bash
# Demonstrates version-blind discovery: the client is given only a project directory and transparently
# gets a working gRPC endpoint - starting (or reusing) a cross-version bridge for the Gradle version
# the project's wrapper declares. No --endpoint, no --gradle-version, no mode chosen by hand. Running
# bridges are tracked in a file-based registry so repeat calls reuse them and each version gets its own.
#
# Usage: GRADLE_BIN=<dist>/bin/gradle PY=<venv>/python ./run-discovery-demo.sh
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
PROTO_ROOT="$(cd "$HERE/.." && pwd)"
PY="${PY:-python3}"
: "${GRADLE_BIN:?set GRADLE_BIN to a gradle launcher that can build the bridge}"

echo "=== building the bridge ==="
"$GRADLE_BIN" -p "$HERE" installDist -q --no-daemon || { echo "bridge build failed"; exit 1; }
export BRIDGE_BIN="$HERE/build/install/grpc-tapi-bridge/bin/grpc-tapi-bridge"

# Isolated, clean bridge registry for the demo.
export GRADLE_GRPC_BRIDGE_HOME="$HERE/.discovery-registry"
rm -rf "$GRADLE_GRPC_BRIDGE_HOME"; mkdir -p "$GRADLE_GRPC_BRIDGE_HOME"
cleanup() {
    for f in "$GRADLE_GRPC_BRIDGE_HOME"/*.json; do
        [ -e "$f" ] || continue
        pid="$("$PY" -c "import json,sys; print(json.load(open(sys.argv[1]))['pid'])" "$f" 2>/dev/null)"
        [ -n "$pid" ] && kill "$pid" 2>/dev/null
    done
    rm -rf "$GRADLE_GRPC_BRIDGE_HOME"
}
trap cleanup EXIT

PASS=0; FAIL=0
check() { # desc exit sub -- <client args...>
    local desc="$1" exp="$2" sub="$3"; shift 3; [ "$1" = "--" ] && shift
    local out ec
    out="$("$PY" "$PROTO_ROOT/client.py" --discover "$@" 2>&1)"; ec=$?
    if [ "$ec" -eq "$exp" ] && printf '%s' "$out" | grep -qF -- "$sub"; then
        echo "PASS  $desc"; PASS=$((PASS+1))
    else
        echo "FAIL  $desc (exit=$ec want=$exp, missing: '$sub')"; printf '%s\n' "$out" | sed 's/^/      | /' | tail -10; FAIL=$((FAIL+1))
    fi
}

APP85="$HERE/legacy-sample"       # wrapper declares Gradle 8.5
APP76="$HERE/legacy-sample-7"     # wrapper declares Gradle 7.6.4

echo "=== discovery: the client is given only a project directory ==="
check "D1 detect 8.5, start a bridge, build" 0 "Hello from the bridged build!"      -- --project-dir "$APP85" hello
check "D2 reuse the running 8.5 bridge"      0 "reusing bridge for Gradle 8.5"      -- --project-dir "$APP85" --query env
check "D3 model served by Gradle 8.5"        0 "gradle version: 8.5"               -- --project-dir "$APP85" --query env
check "D4 route 7.6.4 to its own bridge"     0 "starting a bridge for Gradle 7.6.4" -- --project-dir "$APP76" hello

echo
echo "=== bridge registry now holds one entry per version: ==="
ls "$GRADLE_GRPC_BRIDGE_HOME"/*.json 2>/dev/null | sed 's#.*/#  #'
echo "=== $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]
