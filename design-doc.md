# TestKit API for configuration cache outcome (issue #38223)

## Problem

[Issue #38223](https://github.com/gradle/gradle/issues/38223) (split from [#35595](https://github.com/gradle/gradle/issues/35595)) asks for a TestKit API to assert whether a `GradleRunner` build stored, reused, or discarded its configuration cache (CC) entry. Plugin authors (Spotlight, AndroidX) currently parse console output or the CC HTML report — unsupported surfaces that have already broken under rewording.

The gap is structural:

- The outcome messages are WARN-level log output from `PostBuildProblemsHandler.beforeComplete` in [platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/problems/ConfigurationCacheProblems.kt](platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/problems/ConfigurationCacheProblems.kt) — they vanish under `--quiet` and their wording churns.
- TestKit's `BuildResult` is populated only from captured console output and `OperationType.TASK` progress events ([platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/internal/ToolingApiGradleExecutor.java](platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/internal/ToolingApiGradleExecutor.java)); no structured CC data reaches it.
- The structured truth (the sealed `ConfigurationCacheAction`, the CC store/load build operations) is internal and not bridged to the Tooling API; Gradle's own tests read the internal build-operation trace file, which public TestKit cannot.

## Approach

**A new end-of-build "CC entry outcome" build operation (PR 1), bridged to a new Tooling API progress event `OperationType.CONFIGURATION_CACHE`, consumed by TestKit and exposed on `BuildResult` (PR 2).** Both PRs in one release.

Rejected alternatives:

- **Console parsing in TestKit** — fails under `--quiet` (indistinguishable from CC-off); couples TestKit to log strings across version skew in both directions. Also rejected as a temporary phase: a parser floor below the TAPI-event floor could never be removed without regressing users on older Gradle versions, and same-release PRs make it buy nothing.
- **Marker file via internal system property** — same version floor as the TAPI event, none of the benefits (bespoke protocol, useless to IDEs).
- **Mapping the existing CC store/load build operations** — they fire during configuration, before the final fate is known ("discarded" is decided at build end in `DefaultConfigurationCache.finalizeCacheEntry`); a store followed by a discard would misreport STORED. Hence a new *outcome* operation emitted where the console message is computed, sharing one computation so message and event cannot drift.

Version skew is already solved by the TAPI protocol: subscriptions are strings, and old providers silently drop unknown ones — a new consumer against an old Gradle just gets no events, and TestKit throws `UnsupportedFeatureException` via the existing `TestKitFeature` gating.

## Public API

New in `org.gradle.testkit.runner` (`@Incubating @since 9.8.0`), mirroring `BuildTask.getOutcome()`:

```java
// BuildResult — never null; UnsupportedFeatureException for target Gradle < 9.8
ConfigurationCacheOutcome getConfigurationCacheOutcome();

public enum ConfigurationCacheOutcome {
    NOT_ENABLED,  // CC was not enabled for this build
    STORED,       // miss, new entry stored
    REUSED,       // hit, entry fully reused
    UPDATED,      // partial hit (TAPI model builds under isolated projects)
    DISCARDED,    // entry dropped: problems / too many problems / serialization error / incompatible tasks
    NOT_STORED,   // intentionally nothing stored: read-only-mode miss, graceful degradation
    UNDETERMINED  // CC enabled but build failed before the outcome was decided
}
```

Naming note: `NOT_ENABLED` avoids "DISABLED" because the console says "Configuration cache disabled…" for degradation and read-only misses, which map to `NOT_STORED` here. If #35595 (inputs verification, invalidation reasons) is scheduled later, a richer accessor can be added alongside this convenience enum.

The mapping from provider state is the single `when` block in `ConfigurationCacheProblems.kt` (`fateOfEntryInBuild`): `Load` → REUSED, `Update` → UPDATED, `Store` committed → STORED, `Store` + degradation → NOT_STORED, `Store` + discard / serialization error / too many problems → DISCARDED, `SkipStore` → NOT_STORED, `cacheAction` never initialized → UNDETERMINED, CC off → no operation at all (TestKit reports NOT_ENABLED).

## Architecture — how the outcome travels

The Tooling API reports build activity as **progress events**: a client subscribes to `OperationType`s and receives a start event and a finish event per operation, the finish event carrying a result. The new `CONFIGURATION_CACHE` operation type follows this shape exactly (structural clone of `FILE_DOWNLOAD`, added in 7.3): `ConfigurationCacheStartEvent` is protocol ceremony; all information is on `ConfigurationCacheFinishEvent.getResult()` — a `ConfigurationCacheEntryOutcomeResult` with `getOutcome()` and `getProblemCount()`. The outcome is a `String`, not an enum, so old clients survive future outcome names.

One logical event crosses three boundaries, each with its own types:

1. **Provider (the daemon).** PR 1's "Configuration cache entry outcome" build operation fires at end of build. `ConfigurationCacheOperationMapper` ([platforms/ide/tooling-api-builders/src/main/java/org/gradle/tooling/internal/provider/runner/ConfigurationCacheOperationMapper.java](platforms/ide/tooling-api-builders/src/main/java/org/gradle/tooling/internal/provider/runner/ConfigurationCacheOperationMapper.java), active only when subscribed) converts it into the serializable wire types `DefaultConfigurationCacheDescriptor`/`DefaultConfigurationCacheEntryOutcomeResult` in [platforms/core-runtime/daemon-messaging/src/main/java/org/gradle/internal/build/event/types/](platforms/core-runtime/daemon-messaging/src/main/java/org/gradle/internal/build/event/types/).
2. **Cross-version protocol.** The wire types implement the frozen `InternalConfigurationCache*` interfaces in [platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/protocol/events/](platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/protocol/events/). The subscription is the string constant `CONFIGURATION_CACHE` in `InternalBuildProgressListener`, translated back to the `OperationType` in `OPERATION_TYPE_MAPPING` in [platforms/core-runtime/launcher/src/main/java/org/gradle/tooling/internal/provider/ProviderConnection.java](platforms/core-runtime/launcher/src/main/java/org/gradle/tooling/internal/provider/ProviderConnection.java). Pre-9.8 providers don't recognize the string and drop it — which is why TestKit can subscribe unconditionally.
3. **Consumer (client JVM).** `BuildProgressListenerAdapter` in [platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/parameters/BuildProgressListenerAdapter.java](platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/parameters/BuildProgressListenerAdapter.java) recognizes the internal descriptor and builds the public events in [platforms/ide/tooling-api/src/main/java/org/gradle/tooling/events/configuration/](platforms/ide/tooling-api/src/main/java/org/gradle/tooling/events/configuration/) for the user's listener.

TestKit is then just the first consumer: `ToolingApiGradleExecutor` registers a listener for `OperationType.CONFIGURATION_CACHE`, captures the finish event's outcome string, and threads it through `GradleExecutionResult` into `BuildResult.getConfigurationCacheOutcome()` (null → NOT_ENABLED, unknown name → UNDETERMINED).

## Implementation

### PR 1 — provider-side entry-outcome build operation (IMPLEMENTED, committed on `jb/tide/testkit-cc`)

- `ConfigurationCacheEntryOutcomeBuildOperationType` (Details marker + Result: outcome, problem count) in [platforms/enterprise/enterprise-operations/src/main/java/org/gradle/operations/configuration/ConfigurationCacheEntryOutcomeBuildOperationType.java](platforms/enterprise/enterprise-operations/src/main/java/org/gradle/operations/configuration/ConfigurationCacheEntryOutcomeBuildOperationType.java) — Develocity-consumable, and tooling-api-builders already depends on this project.
- `beforeComplete` in `ConfigurationCacheProblems.kt` computes a single `FateOfEntryInBuild` (outcome + console message) driving **both** the `log(...)` call and the operation emission ([platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/operations/ConfigurationCacheBuildOperations.kt](platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/operations/ConfigurationCacheBuildOperations.kt)). The `lateinit cacheAction` is guarded with `isInitialized` — early-failure builds report UNDETERMINED where they previously crashed.
- Verified by [platforms/core-configuration/configuration-cache/src/integTest/groovy/org/gradle/internal/cc/impl/ConfigurationCacheEntryOutcomeBuildOperationIntegrationTest.groovy](platforms/core-configuration/configuration-cache/src/integTest/groovy/org/gradle/internal/cc/impl/ConfigurationCacheEntryOutcomeBuildOperationIntegrationTest.groovy) (op matches console message across 9 scenarios) and by wiring the op assertion into [testing/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/configurationcache/ConfigurationCacheFixture.groovy](testing/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/configurationcache/ConfigurationCacheFixture.groovy) wherever it asserts a console outcome message — validating the computation across hundreds of existing CC tests. UPDATED needs a TAPI model build under isolated projects, so it has its own test: [platforms/core-configuration/configuration-cache/src/integTest/groovy/org/gradle/internal/cc/impl/isolated/IsolatedProjectsEntryOutcomeBuildOperationIntegrationTest.groovy](platforms/core-configuration/configuration-cache/src/integTest/groovy/org/gradle/internal/cc/impl/isolated/IsolatedProjectsEntryOutcomeBuildOperationIntegrationTest.groovy). The op assertion lives in `assertNotEnabled()`, not `assertNoConfigurationCache()`: read-only misses and degraded builds have no store/load ops but do emit an outcome op.

### PR 2 — Tooling API event + TestKit surface (IMPLEMENTED, on `jb/tide/testkit-cc`, uncommitted)

- The TAPI/protocol/mapper wiring described under Architecture, plus `CONFIGURATION_CACHE` in [platforms/ide/tooling-api/src/main/java/org/gradle/tooling/events/OperationType.java](platforms/ide/tooling-api/src/main/java/org/gradle/tooling/events/OperationType.java).
- TestKit: `ConfigurationCacheOutcome` enum + `getConfigurationCacheOutcome()` on [platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/BuildResult.java](platforms/extensibility/test-kit/src/main/java/org/gradle/testkit/runner/BuildResult.java); unconditional listener in `ToolingApiGradleExecutor`; outcome threaded through `GradleExecutionResult` into `DefaultBuildResult`/`FeatureCheckBuildResult`; `CAPTURE_CONFIGURATION_CACHE_OUTCOME` in `TestKitFeature` gates access against pre-9.8 targets with `UnsupportedFeatureException`.
- Docs: new "Testing with the Configuration Cache" section in [platforms/documentation/docs/src/docs/userguide/reference/plugin-development/test_kit.adoc](platforms/documentation/docs/src/docs/userguide/reference/plugin-development/test_kit.adoc); release notes entry. New `@Incubating` members need no accepted-public-api-changes entry.

## Verification (all green)

- **TestKit functional**: [platforms/extensibility/test-kit/src/integTest/groovy/org/gradle/testkit/runner/GradleRunnerConfigurationCacheOutcomeIntegrationTest.groovy](platforms/extensibility/test-kit/src/integTest/groovy/org/gradle/testkit/runner/GradleRunnerConfigurationCacheOutcomeIntegrationTest.groovy) — NOT_ENABLED, STORED→REUSED, DISCARDED (problems, incompatible task), NOT_STORED (read-only), `--quiet` (the case parsing can't handle), `UnsupportedFeatureException` against the latest released Gradle; embedded and daemon modes.
- **Cross-version**: [platforms/ide/tooling-api/src/crossVersionTest/groovy/org/gradle/integtests/tooling/r98/ConfigurationCacheEntryOutcomeProgressEventCrossVersionSpec.groovy](platforms/ide/tooling-api/src/crossVersionTest/groovy/org/gradle/integtests/tooling/r98/ConfigurationCacheEntryOutcomeProgressEventCrossVersionSpec.groovy) — events on current provider, no events/no failure on `>=6.6 <9.8` providers.
- **Dogfooding**: `ConfigurationCacheTestKitIntegrationTest` migrated from output scraping to the new API. The `ConfigurationCacheFixture`-based tests use the internal executer and cannot adopt the TestKit API — they validate via the PR 1 op instead.
- **Unit + compatibility**: unit tests of all touched modules pass, including the `BuildProgressListenerAdapter` subscription powerset; `:architecture-test:checkBinaryCompatibility` passes.

## Open questions / risks KAKA

1. **Outcome taxonomy needs API review** — chiefly NOT_STORED vs DISCARDED for read-only miss and graceful degradation, and whether UPDATED should fold into STORED.
2. **Event shape**: `BuildOperationMapper` produces an instantaneous start/finish pair — acceptable (BUILD_PHASE precedent) but worth confirming with TAPI owners.
3. **enterprise-operations is a Develocity contract surface** — review the new op type with the DV team; they may want extra fields (entry id, invalidation reasons), which would also serve #35595.
4. Composite builds get one outcome op per invocation (root-build scope), matching console semantics — document it.
