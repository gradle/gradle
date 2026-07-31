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

# run <desc> <expected_exit> <expected_substring> -- <client args...>   (native Python client)
run() {
    local desc="$1" exp_exit="$2" exp_sub="$3"; shift 3
    [ "$1" = "--" ] && shift
    local out ec
    out="$("$PY" "$HERE/client.py" "$@" 2>&1)"; ec=$?
    check "$desc" "$exp_exit" "$exp_sub" "$ec" "$out"
}

# cli <desc> <expected_exit> <expected_substring> -- <gradle args...>   (real gradle CLI)
cli() {
    local desc="$1" exp_exit="$2" exp_sub="$3"; shift 3
    [ "$1" = "--" ] && shift
    local out ec
    out="$("$GRADLE_BIN" "$@" --project-dir "$HERE/sample" 2>&1)"; ec=$?
    check "$desc" "$exp_exit" "$exp_sub" "$ec" "$out"
}

# jvm <desc> <expected_exit> <expected_substring> -- <projectDir>   (classic JVM Tooling API client)
jvm() {
    local desc="$1" exp_exit="$2" exp_sub="$3"; shift 3
    [ "$1" = "--" ] && shift
    local out ec
    out="$(GRADLE_BIN="$GRADLE_BIN" "$HERE/jvm-client/run.sh" "$@" 2>&1)"; ec=$?
    check "$desc" "$exp_exit" "$exp_sub" "$ec" "$out"
}

check() {
    local desc="$1" exp_exit="$2" exp_sub="$3" ec="$4" out="$5"
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
run "11 styled task (built-in tasks)" 0 "BUILD SUCCESSFUL" -- tasks

# CLI-over-gRPC: the real `gradle` command driving the daemon via gRPC (--grpc)
cli "12 CLI over gRPC (--grpc hello)" 0 "Hello from the gRPC-driven build!" -- --grpc hello
cli "13 CLI over gRPC (--grpc boom)"  1 "Intentional failure for the prototype demo" -- --grpc boom

# Plugin-contributed model over Any, targeted per project by logical path. The client stays connected
# to the build root and names the target project (":app"/":lib"); the daemon resolves it via
# BuildTreeModelTarget.ofProject. It decodes IdeProjectModel (a type Gradle does not know) from the
# response Any, and each project yields a distinct model.
run "14 plugin model (root project)"  0 "type.googleapis.com/com.example.ide.IdeProjectModel" -- --query project
run "15 plugin model (:app project)"  0 "project path: :app" -- --query project --target app
run "16 plugin model (:lib project)"  0 "project path: :lib" -- --query project --target lib

# JVM parity: the SAME plugin model fetched over the classic JVM Tooling API (no gRPC). The builder
# returns a protobuf message; the Tooling API adapts it to the client's view interface. Same data as
# the native gRPC client returns above (scenarios 15/16) - one builder serves both clients.
jvm "17 JVM Tooling API parity (:app)" 0 "project path: :app" -- "$HERE/sample/app"
jvm "18 JVM Tooling API parity (:lib)" 0 "project path: :lib" -- "$HERE/sample/lib"

echo "=== $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]
