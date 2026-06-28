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
# 1. Build a local distribution with the prototype
./gradlew install -Pgradle_installPath=/tmp/grpc-gradle --dependency-verification=off

# 2. Use grpcio (a venv is fine)
python3 -m venv /tmp/grpcvenv && /tmp/grpcvenv/bin/pip install grpcio grpcio-tools

# 3. Run a build over gRPC from the non-JVM client
GRADLE_BIN=/tmp/grpc-gradle/bin/gradle /tmp/grpcvenv/bin/python prototype/client.py hello
#   -> streams "Hello from the gRPC-driven build!", exits 0

GRADLE_BIN=/tmp/grpc-gradle/bin/gradle /tmp/grpcvenv/bin/python prototype/client.py boom
#   -> BUILD FAILED, exits 1
```

## Scope (B-only slice)

Runs a build and streams output. No model/state queries (C), no cancellation, no stdin. See
`Claude/plans/2026.06.28-grpc-tooling-api-beta-prototype.md` for the full plan and the documented
prototype simplifications (side-file port advertisement, task-name-only arg handling,
`--dependency-verification=off`).
