# Native gRPC tooling API - Target beta prototype

Proves that a non-JVM client can run a Gradle build and stream its output by talking gRPC
**directly to a server hosted inside the daemon** - no Tooling API layer on the client side.

## Pieces

- `platforms/core-runtime/tooling-api-grpc/` - the wire contract: `tooling.proto` + committed Java stubs.
- `platforms/core-runtime/launcher/.../daemon/server/grpc/` - the in-daemon gRPC server
  (`GrpcDaemonServer`, `ToolingServiceImpl`, `TokenAuthInterceptor`). `RunBuild` drives the daemon's
  existing `BuildExecutor` and tees build output via an `OutputEventListener`, exactly like the Kryo path.
- `gradle --grpc-endpoint` (new flag) - the bootstrap helper. Finds or starts a daemon and prints
  `127.0.0.1:<port> <base64-token>`.
- `prototype/client.py` - the non-JVM client (Python + grpcio). Calls the helper, then dials gRPC.

## Run

```sh
# 1. Build a local distribution with the prototype.
#    --no-build-cache is REQUIRED: the remote build cache otherwise serves stale
#    jars and your changes silently won't take effect.
./gradlew install -Pgradle_installPath=/tmp/grpc-gradle \
    --dependency-verification=off --no-build-cache

# 2. Use grpcio (a venv is fine)
python3 -m venv /tmp/grpcvenv && /tmp/grpcvenv/bin/pip install grpcio grpcio-tools

# 3. Run builds / queries over gRPC from the non-JVM client
export GRADLE_BIN=/tmp/grpc-gradle/bin/gradle
PY=/tmp/grpcvenv/bin/python

$PY prototype/client.py hello          # streams "Hello ...", exit 0
$PY prototype/client.py boom           # daemon-rendered FAILURE, exit 1
$PY prototype/client.py hello -Px=y    # build flags pass through (-P/-D/-x/-q/--info)
$PY prototype/client.py --query env    # query build environment (the C slice)

# CLI-over-gRPC: the real `gradle` command driving the daemon over gRPC instead of the
# internal protocol (a step toward unifying CLI + TAPI + native on one protocol)
$GRADLE_BIN --grpc hello --project-dir prototype/sample   # exit 0
$GRADLE_BIN --grpc boom  --project-dir prototype/sample   # exit 1

# Full scenario suite (13 cases)
PY=$PY ./prototype/run-scenarios.sh
```

If you rebuild after a change, kill daemons first (the dist version is pinned, so old
daemons get reused): `pkill -f org.gradle.launcher.daemon.bootstrap.GradleDaemon`.

## Scope

- **B (run builds)**: tasks + flags via Gradle's real CLI converter, streamed structured output
  (log + styled spans + progress), success/failure exit code.
- **C (query state)**: `QueryModel` returns the build environment (gradle version, java home,
  java version). Richer models (tasks, dependencies) need a model-projection layer - a follow-up.
- **CLI-over-gRPC**: `gradle --grpc <tasks>` runs the build by talking to the daemon over the gRPC
  contract instead of the internal Kryo protocol - the same contract the native client uses.

Documented prototype simplifications (see the plan): side-file port advertisement (not
greeting+registry), pragmatic flag parsing (not the full CLI converter), `--dependency-verification=off`,
no cancellation/stdin. Full details + decisions in
`Claude/plans/2026.06.28-grpc-tooling-api-beta-prototype.md`.
