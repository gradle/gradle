# Tooling API: Daemon Management (with Gradle 5.0+ cross-version support)

## Context

Today's Gradle Tooling API has **no public surface for managing daemons**. The only daemon-adjacent calls are:
- `GradleConnector.disconnect()` — only affects daemons started by *that* connector
- `ProjectConnection.notifyDaemonsAboutChangedPaths(...)` — file-change notification, not lifecycle

Meanwhile the `--status` and `--stop` CLI commands have rich infrastructure behind them (`DaemonRegistry`, `DaemonStopClient`, `ReportDaemonStatusClient`, `DaemonInfo`, `DaemonContext`). IDEs (IntelliJ, Buildship) cannot show running daemons or stop individual ones — they shell out to `gradle --stop`, which itself only stops daemons whose registry it can deserialize.

**Goal:** add a public, IDE-facing daemon-management API on `GradleConnector` that:
- Lists daemons across **every** Gradle version under the user's Gradle home (5.0+, the practical floor since `MINIMUM_SUPPORTED_GRADLE_VERSION = 4.0` in `DefaultGradleConnector.java:34`)
- Stops daemons individually or as a group
- Tolerates the registry-format break at Gradle 8.8 by carrying version-specific decoders

## API Shape

New package `org.gradle.tooling.daemon`, all interfaces `@Incubating`.

```java
// GradleConnector
public abstract DaemonManagement newDaemonManagement();
// Honors useGradleUserHomeDir(File) if previously called; otherwise default ~/.gradle.

// DaemonManagement
List<GradleDaemon> listDaemons();
List<GradleDaemon> listDaemons(String gradleVersion);
void stopAll();
StopResult stop(GradleDaemon daemon);
StopResult stopByPid(long pid);
StopResult stopByUid(String uid);

// GradleDaemon
String getUid();  long getPid();
DaemonStatus getStatus();         // IDLE | BUSY | CANCELED | STOPPED | UNKNOWN
String getGradleVersion();
File getJavaHome();
@Nullable Integer getJavaMajorVersion();   // null for pre-8.8 daemons
@Nullable String  getJavaVendor();          // null for pre-8.10 daemons
Duration getIdleTimeout();
List<String> getJvmArguments();
Instant getLastBusy();

// StopResult: STOPPED | NOT_FOUND | TIMED_OUT
```

## Cross-Version Registry Reading

The daemon registry binary format has shifted twice in the 5.0–9.x window — `DefaultDaemonContext.SERIALIZER`:

| Era | nativeServices field | javaVersion | javaVendor | Commit |
|-----|----------------------|-------------|------------|--------|
| V1 (5.0 – 8.7)  | `readBoolean()` `useNativeServices` | absent | absent | `9fc5f916b10` |
| V2 (8.8 – 8.9)  | `readSmallInt()` `NativeServicesMode.ordinal()` | `readString()` | absent | `5c4faea8868`, `420f310f972` |
| V3 (8.10+)      | `readSmallInt()` | `readString()` | `readString()` | `fc5f53768ec` |

The outer envelope (`DaemonRegistryContent`: addresses, DaemonInfo array, StopEvent array) has been stable since Gradle 3.1, so only the nested `DaemonContext` decode differs.

**Strategy: bundled version-specific decoders.** The consumer jar ships frozen byte-layout readers per era. Each reader implements the layout directly with `KryoBackedDecoder` (already on the consumer classpath via `:serialization`). The directory name under `<gradleUserHome>/daemon/` selects which decoder to use.

Why this works: the binary format was never *intentionally* versioned (no magic byte / format header), so dispatch by Gradle version is the only reliable signal. Directory name is set by the daemon process itself, so it cannot disagree with the contents.

## Reader Class Structure

`platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/daemon/registry/`:

```java
// Internal value carrying only what we expose + what we need to stop.
// nativeServicesMode and priority bytes are consumed-and-discarded by the
// readers so subsequent field offsets stay correct.
final class DaemonInfoView {
    final String uid; final long pid;
    final String javaHome;
    final String javaVersion;       // null for V1 (pre-8.8)
    final String javaVendor;        // null for V1, V2 (pre-8.10)
    final Duration idleTimeout; final List<String> jvmArguments;
    final InetSocketAddress address; final byte[] token;   // stop path
    final State state; final Instant lastBusy;
}

interface RegistryFormatReader {
    List<DaemonInfoView> read(InputStream in) throws IOException;
}

final class RegistryFormatReaderV1 implements RegistryFormatReader { ... }  // 5.0 – 8.7
final class RegistryFormatReaderV2 implements RegistryFormatReader { ... }  // 8.8 – 8.9
final class RegistryFormatReaderV3 implements RegistryFormatReader { ... }  // 8.10+

final class RegistryReader {
    private static final GradleVersion V_8_8  = GradleVersion.version("8.8");
    private static final GradleVersion V_8_10 = GradleVersion.version("8.10");

    List<DaemonInfoView> read(File registryBin, String gradleVersion) {
        GradleVersion v = GradleVersion.version(gradleVersion);
        RegistryFormatReader r =
            v.compareTo(V_8_10) >= 0 ? new RegistryFormatReaderV3() :
            v.compareTo(V_8_8)  >= 0 ? new RegistryFormatReaderV2() :
                                       new RegistryFormatReaderV1();
        try (var in = new BufferedInputStream(new FileInputStream(registryBin))) {
            return r.read(in);
        } catch (Exception e) {
            return List.of();  // best-effort: corrupted or partial-write registry returns empty
        }
    }
}
```

Most fields populate for every era. `javaMajorVersion` is `null` for pre-8.8 daemons (the V1 binary format had no `javaVersion` field); `javaVendor` is `null` for pre-8.10 daemons. The IDE should render those cells as "unknown" for those entries.

`javaMajorVersion` is derived from the raw `javaVersion` string via `Integer.parseInt(version.split("\\.")[0])` with a fallback to `null` on parse failure — guards against the historical "1.8" Java version format.

**Maintenance:** every future change to `DefaultDaemonContext` serialization adds a new `RegistryFormatReaderVN`. Old ones never change. A new unit test (see Verification) compares V_current's output against the live `DefaultDaemonContext.SERIALIZER` to catch drift on future format changes — the test fails CI before the new format ships.

## Stop Path

Once we have `address + token` from `DaemonInfoView` (both stable across all eras), stopping is the same for every version:

```java
new DaemonStopClient(daemonConnector, idGenerator).gracefulStop(List.of(connectDetails));
```

`Stop` / `StopWhenIdle` protocol messages have been stable since Gradle 2.2 (`Stop.java`, `StopWhenIdle.java` — 3 lines each, never changed).

Reused: `platforms/core-runtime/client-services/src/main/java/org/gradle/launcher/daemon/client/DaemonStopClient.java`.

## Files to Create

Public API — `platforms/ide/tooling-api/src/main/java/org/gradle/tooling/daemon/`:
- `DaemonManagement.java`, `GradleDaemon.java`, `DaemonStatus.java`, `StopResult.java`, `package-info.java`

Consumer internals — `platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/daemon/`:
- `DefaultDaemonManagement.java`
- `DefaultGradleDaemon.java`
- `GradleVersionDaemonRegistries.java` — enumerates `<gradleUserHome>/daemon/*/`
- `DaemonStatusMapper.java`, `StopResultMapper.java`

Registry decoders — `.../internal/consumer/daemon/registry/`:
- `DaemonInfoView.java`
- `RegistryFormatReader.java`
- `RegistryFormatReaderV1.java`, `RegistryFormatReaderV2.java`, `RegistryFormatReaderV3.java`
- `RegistryReader.java`

## Files to Modify

- `platforms/ide/tooling-api/src/main/java/org/gradle/tooling/GradleConnector.java` — abstract `newDaemonManagement()`.
- `platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/DefaultGradleConnector.java` — implement.
- `platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/ConnectorServices.java` — wire `DaemonManagementFactory`.
- `platforms/ide/tooling-api/build.gradle.kts` — confirm `client-services` and `daemon-protocol` are on the consumer runtime classpath (currently transitive via `:launcher`; make explicit if needed).

## Verification

### Unit tests (`platforms/ide/tooling-api/src/test/groovy/...`)

- `RegistryFormatReaderV1Test` / `V2Test` / `V3Test` — feed each reader a fixture `registry.bin` byte array captured from a real daemon of that era, assert decoded `DaemonInfoView` matches expected.
- `RegistryReaderDispatchTest` — verifies version-to-reader mapping at the 8.8 and 8.10 boundaries.
- **Drift guard** `CurrentFormatDriftTest` — round-trips through `DefaultDaemonContext.SERIALIZER` (current code) and `RegistryFormatReaderV3` (our frozen reader), asserts bytes match. **Fails CI when a contributor changes the format without adding a new RegistryFormatReaderVN.**

### Cross-version integration tests (`src/crossVersionTest/`)

Use the existing `gradlebuild.cross-version-tests` plugin already configured in `tooling-api/build.gradle.kts:6`. New file: `DaemonManagementCrossVersionTest.groovy` extending `ToolingApiSpecification`.

Test matrix — all major versions plus the format boundaries:
```
@TargetGradleVersion(">=5.0")  // covers 5.0, 6.0, 7.0, 8.0, 8.7, 8.8, 8.10, 9.0, current
```

Per-version scenarios:
1. Spawn a daemon of `${target.version}` by running a no-op build via `ProjectConnection.newBuild().run()`.
2. Construct `DaemonManagement` via `GradleConnector.newConnector().useGradleUserHomeDir(daemonBaseDir).newDaemonManagement()`.
3. Assert `listDaemons()` contains an entry with the spawned PID, `gradleVersion == target.version`, correct `javaHome`.
4. For 8.8+: assert `javaMajorVersion` is non-null.
5. For 8.10+: assert `javaVendor` is non-null.
6. For 5.0–8.7: assert `javaMajorVersion` is null but `idleTimeout` and `pid` are correct.
7. Multi-version: spawn two daemons of different versions (e.g. 5.0 + current) in the same gradleUserHome — assert `listDaemons()` returns both.
8. Stop: `dm.stopByPid(pid)` then poll registry; assert daemon vanishes.
9. Negative: `stopByPid(99_999_999)` returns `NOT_FOUND` without throwing.

These run under `embeddedIntegTest`-equivalent for cross-version (the cross-version plugin wires its own task — typically `:tooling-api:gradle<version>CrossVersionTest`). Local run:
```
./gradlew :tooling-api:crossVersionTest -PtestVersions=5.0,8.7,8.8,8.10,current
```
working dir: `/Users/asodja/workspace/agents`.

CI matrix is configured in the build's existing cross-version test infrastructure (no extra wiring needed beyond placing the test in `src/crossVersionTest/`).

## Out of Scope (Future)

- Async / `ResultHandler` variants — additive, sync API ships first.
- `stopWhenIdle` — protocol message exists, not in user's initial requirements.
- Daemon log retrieval / live tail.
