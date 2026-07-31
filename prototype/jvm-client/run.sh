#!/usr/bin/env bash
# Compiles and runs the JVM Tooling API parity client. Fetches the plugin-contributed IdeProjectModel
# over the classic Tooling API (no gRPC), reusing the same generated protobuf model as the plugin.
#
# Usage: GRADLE_BIN=<dist>/bin/gradle ./run.sh [projectDir]
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PROTO_ROOT="$(cd "$HERE/.." && pwd)"
: "${GRADLE_BIN:?set GRADLE_BIN to the installed gradle launcher}"
DIST="$(cd "$(dirname "$(dirname "$GRADLE_BIN")")" && pwd)"
PROJECT_DIR="${1:-$PROTO_ROOT/sample}"

TAPI_JAR="$(ls "$DIST"/lib/gradle-tooling-api-*.jar | head -1)"
PROTOBUF_JAR="$(ls "$DIST"/lib/protobuf-java-*.jar | head -1)"
SLF4J_JAR="$(ls "$DIST"/lib/slf4j-api-*.jar | head -1)"
GEN_SRC="$PROTO_ROOT/sample/buildSrc/src/main/java"

OUT="$HERE/build/classes"
rm -rf "$OUT"; mkdir -p "$OUT"

# Compile the client (Main + view interface) together with the plugin's generated protobuf model
# (the client deserializes the concrete protobuf message, then the Tooling API adapts it to the view).
SOURCES="$OUT/sources.txt"
find "$HERE/src" -name '*.java' > "$SOURCES"
ls "$GEN_SRC"/com/example/ide/proto/*.java >> "$SOURCES"
javac -cp "$TAPI_JAR:$PROTOBUF_JAR" -d "$OUT" @"$SOURCES"

# The distribution's tooling-api jar is the unshaded module jar, so it needs the other runtime jars
# (unlike the standalone published gradle-tooling-api artifact). Put the whole lib dir on the run
# classpath for the prototype.
LIB_CP="$(ls "$DIST"/lib/*.jar | tr '\n' ':')"
java -cp "$OUT:$LIB_CP" \
    com.example.ide.client.JvmToolingApiClient "$PROJECT_DIR" "$DIST"
