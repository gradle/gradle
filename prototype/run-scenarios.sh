#!/usr/bin/env bash
# Extensive scenario tests for the native gRPC tooling API prototype (Target beta).
#
# Usage: GRADLE_BIN=<dist>/bin/gradle PY=<venv>/python ./run-scenarios.sh
set -u

HERE="$(cd "$(dirname "$0")" && pwd)"
PY="${PY:-python3}"
: "${GRADLE_BIN:?set GRADLE_BIN to the installed gradle launcher}"

PASS=0
FAIL=0

# run <desc> <expected_exit> <expected_substring> -- <client args...>
run() {
    local desc="$1" exp_exit="$2" exp_sub="$3"; shift 3
    [ "$1" = "--" ] && shift
    local out ec
    out="$("$PY" "$HERE/client.py" "$@" 2>&1)"; ec=$?
    if [ "$ec" -eq "$exp_exit" ] && printf '%s' "$out" | grep -qF -- "$exp_sub"; then
        echo "PASS  $desc"
        PASS=$((PASS+1))
    else
        echo "FAIL  $desc  (exit=$ec want=$exp_exit, missing substring: '$exp_sub')"
        printf '%s\n' "$out" | sed 's/^/      | /' | tail -8
        FAIL=$((FAIL+1))
    fi
}

echo "=== gRPC tooling API prototype - scenario tests ==="
run "1  hello (success)"              0 "Hello from the gRPC-driven build!" -- hello
run "2  boom (failure -> exit 1)"     1 "Intentional failure for the prototype demo" -- boom
run "3  built-in help task"           0 "Welcome to Gradle" -- help
run "4  multiple tasks (hello help)"  0 "Hello from the gRPC-driven build!" -- hello help
run "5  exclude task (-x boom)"       0 "Hello from the gRPC-driven build!" -- hello boom -x boom
run "6  project property (-P)"        0 "Hello from the gRPC-driven build!" -- hello -Pfoo=bar
run "7  quiet (-q)"                   0 "Hello from the gRPC-driven build!" -- hello -q
run "8  unknown task (-> exit 1)"     1 "not found" -- doesnotexist
run "9  query build environment"      0 "gradle version:" -- --query env
run "10 reuse daemon (2nd hello)"     0 "Hello from the gRPC-driven build!" -- hello

echo "=== $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]
