# "Configuring 100%" Should Not Have Work Inside

## Issue Summary

When Gradle displays `CONFIGURING 100%`, it implies all configuration work is done. In reality, significant work
still happens while the progress bar shows 100%:

1. **Task graph calculation** — `Calculate task graph` build operation runs after all projects are configured
   but while still in the Configuring phase.
2. **Configuration cache store** — after a full configuration run, serializing the build state to the CC happens
   while still showing `CONFIGURING 100%`.
3. **Configuration cache load** — on cache hit, the entire build state is deserialized while showing `CONFIGURING 100%`.

PR [#36927](https://github.com/gradle/gradle/pull/36927) (merged 2026-03-04) improved observability by adding a
`progressDisplayName("Building task graph of root build")` to the task graph operation. This shows the operation
name in the detailed build scan / console output, but the progress bar still reads `CONFIGURING 100%`.

Related issue: [#29941](https://github.com/gradle/gradle/issues/29941) — requests progress feedback during CC load/store.

---

## How the Progress Bar Works

The progress bar is driven by `BuildStatusRenderer` and `ProgressBar`.

### Phase lifecycle (`BuildStatusRenderer`)

```
Initializing  →  Configuring  →  Executing
```

| Build operation category      | Effect                                              |
|-------------------------------|-----------------------------------------------------|
| `CONFIGURE_ROOT_BUILD`        | Transitions to **Configuring** phase, sets total    |
| `CONFIGURE_BUILD`             | Adds to total (`moreProgress`)                      |
| `CONFIGURE_PROJECT` complete  | Increments current (`update`)                       |
| `RUN_MAIN_TASKS`              | Transitions to **Executing** phase                  |
| `RUN_WORK`                    | Adds to Executing total                             |
| `TASK` / `TRANSFORM` complete | Increments Executing current                        |

Relevant code:
- `BuildStatusRenderer.java:78-95` — event dispatch / phase transitions
- `BuildStatusRenderer.java:123-141` — `phaseStarted`, `phaseHasMoreProgress`, `phaseProgressed`

### Progress total is set at `CONFIGURE_ROOT_BUILD` start

```java
// BuildOperationFiringProjectsPreparer.java:64
builder.totalProgress(gradle.getSettings().getProjectRegistry().size());
```

The total equals the number of projects known when the `Configure build` operation starts.
Each `CONFIGURE_PROJECT` completion increments the counter. Once the last project completes,
`current == total` → **100% is displayed**.

But the `CONFIGURE_ROOT_BUILD` operation is still open — work continues inside it.

---

## What Happens After "Configuring 100%"

### Scenario 1: Non-CC build (or CC miss)

```
CONFIGURE_ROOT_BUILD (open)
  ├── CONFIGURE_PROJECT :a   ← tracked, increments progress
  ├── CONFIGURE_PROJECT :b
  ├── ...
  ├── CONFIGURE_PROJECT :z   ← last one, now current==total → 100% shown
  └── Calculate task graph   ← UNCATEGORIZED — NOT tracked, but work is happening!
RUN_MAIN_TASKS               ← only here does "Executing" begin
```

The `Calculate task graph` operation has no `BuildOperationCategory` metadata (defaults to `UNCATEGORIZED`).
`BuildStatusRenderer` ignores it, so the bar stays frozen at 100% while Gradle resolves dependency graph,
adds finalizers, and walks the task execution plan.

Relevant code:
- `BuildOperationFiringBuildWorkPreparer.java:110-120` — task graph build operation (no category set)
- `BuildStatusRenderer.java:81-95` — only `CONFIGURE_BUILD`/`CONFIGURE_PROJECT`/`RUN_WORK`/top-level work
  items are tracked; `UNCATEGORIZED` is ignored

### Scenario 2: Configuration cache store

After all projects configure (100%), `DefaultConfigurationCache` serializes the entire work graph:

```
CONFIGURE_ROOT_BUILD (open)
  └── Calculate task graph (UNCATEGORIZED)
        └── (CC store fires here or right after)
              Store configuration cache state   ← progressDisplayName set, but NOT tracked in bar
```

Operation defined in `ConfigurationCacheBuildOperations.kt:55-68`:
```kotlin
BuildOperationDescriptor
    .displayName("Store configuration cache state")
    .progressDisplayName("Storing configuration cache state")
    // No metadata / category → UNCATEGORIZED → ignored by BuildStatusRenderer
```

### Scenario 3: Configuration cache load (cache hit)

On a cache hit, the project registry may be empty (no `CONFIGURE_PROJECT` ops fire at all) or
partially populated. The progress bar jumps straight to 100% (or never moves from 0), and then
the expensive deserialization happens:

```
CONFIGURE_ROOT_BUILD (open, totalProgress may be 0 or 1)
  └── Load configuration cache state   ← UNCATEGORIZED — NOT tracked
```

Operation defined in `ConfigurationCacheBuildOperations.kt:39-51`:
```kotlin
BuildOperationDescriptor
    .displayName("Load configuration cache state")
    .progressDisplayName("Loading configuration cache state")
    // No metadata / category → ignored
```

### Scenario 4: Configure-on-Demand (CoD)

With CoD enabled (`--configure-on-demand`), `DefaultProjectsPreparer` skips project configuration
entirely during the project preparation phase and returns immediately:

```java
// DefaultProjectsPreparer.java:42-45
public void prepareProjects(GradleInternal gradle) {
    if (skipEvaluationDuringProjectPreparation(buildModelParameters, gradle)) {
        return;  // ← CoD: returns immediately, no projects configured here
    }
    ...
}

// DeferredProjectEvaluationCondition.java:26-31
public static boolean skipEvaluationDuringProjectPreparation(...) {
    return buildModelParameters.isConfigureOnDemand() && gradle.isRootBuild();
}
```

The `CONFIGURE_ROOT_BUILD` operation still fires with `totalProgress = N` (all projects in the
registry), but immediately completes with **zero** `CONFIGURE_PROJECT` children. Projects are then
configured lazily on-demand **inside** the `Calculate task graph` operation as tasks request them.

```
CONFIGURE_ROOT_BUILD   totalProgress=N, fires 0 CONFIGURE_PROJECT ops → completes instantly
                       bar: 0% CONFIGURING

Calculate task graph   (UNCATEGORIZED — still in Configuring phase)
  ├── CONFIGURE_PROJECT :a   ← on-demand: tracked, current=1
  ├── CONFIGURE_PROJECT :b   ← on-demand: tracked, current=2
  ...only M ≤ N projects get configured...
                       bar: M/N * 100% CONFIGURING (never reaches 100% if M < N)

RUN_MAIN_TASKS         → Executing phase
```

`BuildStatusRenderer` DOES track the on-demand `CONFIGURE_PROJECT` completions (they fire while
`currentPhase == Phase.Configuring`), but `total` was set to N upfront. The bar can only reach
`M/N * 100%` and **never hits 100%** unless all projects happen to be needed.

**How Fix B interacts with CoD:**
With Fix B, `CALCULATE_TASK_GRAPH` adds +1 to total (total = N+1). On-demand `CONFIGURE_PROJECT`
ops fire inside it (current reaches M), then it completes (current = M+1). The bar shows
`(M+1)/(N+1) * 100%` — still not 100% if M < N. The fundamental problem is the same: `totalProgress`
was set to the wrong number upfront.

**The root issue for CoD:** the progress total cannot be known at `CONFIGURE_ROOT_BUILD` start
because CoD defers the decision of which projects to configure until task graph time.

**Possible approaches for CoD specifically:**

1. **Set `totalProgress = 0` for CoD** and call `moreProgress(1)` on each `CONFIGURE_PROJECT`
   *start* event (not just on `CONFIGURE_BUILD`). This sounds natural but **causes oscillation**:
   every time a project completes before the next one starts, `current == total` → bar flashes 100%,
   then drops back when the next project starts. Step-by-step for sequential CoD:

   ```
   CONFIGURE_ROOT_BUILD starts  → total=0, current=0  →  0%
   :a starts  → moreProgress(1) → total=1, current=0  →  0%
   :a done    → update()        → total=1, current=1  → 100%  ← flash!
   :b starts  → moreProgress(1) → total=2, current=1  → 50%
   :b done    → update()        → total=2, current=2  → 100%  ← flash!
   :c starts  → moreProgress(1) → total=3, current=2  → 66%
   :c done    → update()        → total=3, current=3  → 100%  ← flash!
   ```

   With **parallel configuration** the oscillation is much less severe — multiple projects start
   before any complete, so `total` grows faster than `current` and 100% only appears at the true end:

   ```
   :a,:b,:c start → total=3, current=0 →  0%
   :a done        → total=3, current=1 → 33%
   :b done        → total=3, current=2 → 66%
   :c done        → total=3, current=3 → 100%  ← only once, at true end
   ```

   For sequential CoD (the default), render ticks fire every ~100ms. Real Android projects take
   seconds to configure, so users would see repeated 100% flashes between projects.

2. **Indeterminate / count-only mode:** When CoD is active, skip the percentage and show a
   monotonically increasing count instead. The count increments on each `CONFIGURE_PROJECT`
   completion and never goes backwards — no oscillation, no false 100%:

   ```
   CONFIGURE_ROOT_BUILD starts  → [···············] CONFIGURING [0ms]
   :a done                      → [···············] CONFIGURING [1 project] [0.5s]
   :b done                      → [···············] CONFIGURING [2 projects] [1.1s]
   :c done                      → [···············] CONFIGURING [3 projects] [1.8s]
   RUN_MAIN_TASKS               → [···············] 0% EXECUTING [1.8s]
   ```

   The bar itself stays empty (or animates as a spinner) since there is no meaningful percentage
   to fill. The suffix switches from `N%` to `[N project(s)]` when CoD is active.

   This requires changes to `ProgressBar` / `BuildStatusRenderer`:
   - `ProgressBar` needs an indeterminate mode where `total = 0` renders a count label instead
     of a percentage (currently `0/0` integer-casts to `0%` and the bar just stays empty silently)
   - `BuildStatusRenderer` needs to detect CoD and pass `totalProgress = 0` to `phaseStarted`,
     then call a new `phaseProjectConfigured()` on `CONFIGURE_PROJECT` complete that increments
     the count label rather than `current`
   - Or simpler: reuse `current` as the count and format the suffix conditionally based on whether
     `total == 0`

   **Behaviour is honest and monotonic** — users see progress happening and never get a premature
   100%. The trade-off is no indication of how far through configuration we are, just raw counts.

3. **Accept the inaccuracy for CoD:** Leave CoD showing a partial percentage (`M/N * 100%`). It
   shows *increasing* progress and is at least not misleadingly stuck at 100%.

4. **Fix C (separate Planning phase)** sidesteps the problem entirely: `CONFIGURE_ROOT_BUILD`
   completes with whatever percentage was reached, then a new `PLANNING` phase covers task graph
   calculation and on-demand project configuration. The phase label itself signals work is happening,
   and `PLANNING 0% → 100%` is accurate because the task graph completion is the single known unit.

**Isolated Projects (IP) note:** IP explicitly disables CoD's lazy project skipping — all projects
are configured even if not all are needed for the requested tasks (IP docs, line 51: *"Parallel
configuration does not support Configuration on Demand"*). So with IP, the full project count IS
configured and Fix B applies cleanly.

Relevant code:
- `DefaultProjectsPreparer.java:42-45` — CoD early return
- `DeferredProjectEvaluationCondition.java:26-31` — CoD condition
- `BuildOperationFiringProjectsPreparer.java:64` — total set to N regardless of CoD

---

## Root Cause

The `CONFIGURING` progress bar only counts `CONFIGURE_PROJECT` completions. Any work that happens
inside the `CONFIGURE_ROOT_BUILD` operation but is not a `CONFIGURE_PROJECT` is invisible to the
progress bar. This includes:

1. Task graph calculation (`BuildOperationFiringBuildWorkPreparer`)
2. CC serialization / deserialization (`DefaultConfigurationCache` / `ConfigurationCacheBuildOperations`)
3. Dependency resolution during configuration (not typically separated out)

---

## Possible Fixes

### Fix A: Cap display at 99% until `RUN_MAIN_TASKS` fires (quick UX band-aid)

In `ProgressBar.formatProgress`, clamp the displayed percentage to 99 unless the phase is complete:

```java
// ProgressBar.java:172 — change percentage calculation
int progressPercent = total > 0 ? Math.min(99, (int) (current * 100.0 / total)) : 0;
```

Then allow 100% only when the *phase* is explicitly complete (e.g. when `RUN_MAIN_TASKS` fires and
`BuildStatusRenderer` transitions out of Configuring). This approach is a **display-only fix** — it
doesn't improve accuracy, just prevents the misleading "100%".

**Pros:** Simple, low-risk.
**Cons:** The bar shows 99% forever until Executing starts. Users might think the build is stuck.

---

### Fix B: Add a new `CALCULATE_TASK_GRAPH` category and track it

The `+1` for task graph and `+1` for each CC operation are carried on the **operation descriptor**
via `totalProgress(1)`. `BuildStatusRenderer` reads them via `phaseHasMoreProgress(startEvent.getTotalProgress())`
when it sees the operation start — the same pattern used by `CONFIGURE_BUILD` for nested builds.

**Do NOT pre-add +1 in `BuildOperationFiringProjectsPreparer`** — that would tightly couple
configuration to scheduling and would leave the bar stuck at ~99% if the task graph op ever didn't fire.

#### Step 1 — New category in `BuildOperationCategory.java`

```java
// platforms/core-runtime/base-services/src/main/java/org/gradle/internal/operations/BuildOperationCategory.java
CALCULATE_TASK_GRAPH(true, false, false),
```

#### Step 2 — Set category + `totalProgress(1)` on the task graph operation

```java
// BuildOperationFiringBuildWorkPreparer.java:110-119
@Override
public BuildOperationDescriptor.Builder description() {
    return BuildOperationDescriptor.displayName(gradle.contextualize("Calculate task graph"))
        .progressDisplayName(buildingTaskGraphDisplayName(gradle))
        .metadata(BuildOperationCategory.CALCULATE_TASK_GRAPH)  // ← new
        .totalProgress(1)                                        // ← new
        .details(...);
}
```

#### Step 3 — Set category + `totalProgress(1)` on CC store and load operations

```kotlin
// ConfigurationCacheBuildOperations.kt:57-59  (store)
BuildOperationDescriptor
    .displayName("Store configuration cache state")
    .progressDisplayName("Storing configuration cache state")
    .metadata(BuildOperationCategory.CALCULATE_TASK_GRAPH) // or a dedicated CC category
    .totalProgress(1)                                       // ← new

// ConfigurationCacheBuildOperations.kt:41-44  (load)
BuildOperationDescriptor
    .displayName("Load configuration cache state")
    .progressDisplayName("Loading configuration cache state")
    .metadata(BuildOperationCategory.CALCULATE_TASK_GRAPH) // or a dedicated CC category
    .totalProgress(1)                                       // ← new
```

#### Step 4 — Track in `BuildStatusRenderer`

Unlike `CONFIGURE_BUILD` (which only adds to total, not tracked for completion), these operations
need **both**: add to total when they start AND increment current when they complete.

```java
// BuildStatusRenderer.java — new branch in the ProgressStartEvent block (78-95)
} else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.CALCULATE_TASK_GRAPH
           && currentPhase == Phase.Configuring) {
    phaseHasMoreProgress(startEvent);                           // total += 1
    currentPhaseChildren.add(startEvent.getProgressOperationId()); // track for completion
}
```

When the operation completes, it is already in `currentPhaseChildren`, so the existing
`phaseProgressed` path fires automatically and calls `progressBar.update()` → `current++`.

#### Resulting sequence (non-CC, N projects)

```
CONFIGURE_ROOT_BUILD starts  → total = N,   current = 0  →  0%
CONFIGURE_PROJECT × N done   → total = N,   current = N  → 100%  (brief, no render tick expected)
CALCULATE_TASK_GRAPH starts  → total = N+1, current = N  → ~99%
CALCULATE_TASK_GRAPH done    → total = N+1, current = N+1 → 100%  (truly done)
RUN_MAIN_TASKS               → Executing phase begins
```

The brief 100% at step 2 is harmless — steps 2 and 3 happen synchronously with no render tick
between them (`UpdateNowEvent` is time-based).

**Pros:** Accurate — 100% truly means configuration + task graph calculation are done.
**Cons:** Requires new category; the task graph and CC operations each need `totalProgress(1)` added.

---

### Fix C: Add a new build phase "Planning" between Configuring and Executing

Instead of cramming task graph calculation into the Configuring phase, introduce a third phase:

```
Initializing → Configuring → Planning → Executing
```

- `CONFIGURE_ROOT_BUILD` complete → enter Planning
- `CALCULATE_TASK_GRAPH` start → Planning phase (reset bar), complete → 100% planning
- `RUN_MAIN_TASKS` → enter Executing

This requires:
- New `Phase.Planning` in `BuildStatusRenderer`
- New `BuildOperationCategory.CALCULATE_TASK_GRAPH` (or reuse the existing build operation)
- `BuildStatusRenderer` to transition on it

**Pros:** Clearest UX — users see distinct Configuring / Planning / Executing.
**Cons:** More invasive change; changes visible user-facing phase labels.

---

### Fix D: Add progress to CC load/store operations (issue #29941)

For configuration cache scenarios, emit sub-progress events during load/store.
This requires tracking how many work-graph nodes need to be serialized/deserialized.

High-level approach:
1. Add `metadata(BuildOperationCategory.CONFIGURE_BUILD)` (or a new CC-specific category) and
   `totalProgress(N)` to `withWorkGraphLoadOperation` / `withWorkGraphStoreOperation`.
2. Emit `CONFIGURE_PROJECT`-equivalent progress events for each project's state being serialized
   or deserialized.
3. `BuildStatusRenderer` would then track these the same way it tracks `CONFIGURE_PROJECT`.

Relevant code:
- `ConfigurationCacheBuildOperations.kt:39-94` — where load/store operations are defined
- `DefaultConfigurationCache.kt` — orchestrates the CC lifecycle

**Pros:** Directly addresses issue #29941.
**Cons:** Requires changes to CC internals; need to know total number of items before starting.

---

## Key Files Reference

| File | Purpose | Key Lines |
|------|---------|-----------|
| `platforms/core-runtime/logging/src/main/java/org/gradle/internal/logging/console/BuildStatusRenderer.java` | Phase transitions, progress tracking | 78–95, 123–141 |
| `platforms/core-runtime/logging/src/main/java/org/gradle/internal/logging/console/ProgressBar.java` | Progress bar rendering, % calculation | 142–161, 172 |
| `subprojects/core/src/main/java/org/gradle/configuration/BuildOperationFiringProjectsPreparer.java` | Sets `totalProgress` for Configuring phase | 57–72 |
| `subprojects/core/src/main/java/org/gradle/configuration/BuildTreePreparingProjectsPreparer.java` | Orchestrates CC, buildSrc, project loading | 49–68 |
| `subprojects/core/src/main/java/org/gradle/internal/build/BuildOperationFiringBuildWorkPreparer.java` | "Calculate task graph" build operation | 109–128 |
| `platforms/core-runtime/base-services/src/main/java/org/gradle/internal/operations/BuildOperationCategory.java` | Enum of all tracked categories | all |
| `platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/operations/ConfigurationCacheBuildOperations.kt` | CC load/store build operations | 39–94 |
| `platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/DefaultConfigurationCache.kt` | CC lifecycle orchestration | overall |
| `subprojects/core/src/main/java/org/gradle/initialization/VintageBuildModelController.java` | Non-CC build model lifecycle | all |
| `platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/ConfigurationCacheAwareBuildModelController.kt` | CC-aware build model lifecycle | all |

---

## Testing

### Unit tests — fastest feedback loop

`BuildStatusRendererTest.groovy` is the primary unit test for Fix B. It fires fake
`ProgressStartEvent` / `ProgressCompleteEvent` objects directly into `BuildStatusRenderer` and
asserts the resulting status bar string (e.g. `[###............] 25% CONFIGURING [0ms]`).

```
./gradlew :logging:test --tests "*.BuildStatusRendererTest"
```

The test helpers make it easy to add a new scenario. The existing `startOther` helper already
shows how to fire an `UNCATEGORIZED` operation — a new `startCalculateTaskGraph` helper would just
pass the new category:

```groovy
// in BuildStatusRendererTest.groovy — new helper alongside startConfigureProject etc.
def startCalculateTaskGraph(Long id, Long parentId) {
    return start(id, parentId, BuildOperationCategory.CALCULATE_TASK_GRAPH, 1)
}
```

A new unit test case for Fix B would look like:

```groovy
def "calculate task graph is tracked as part of configuring phase"() {
    given:
    def event1 = startRootBuildOperation(1)
    def event2 = startConfigureRootBuild(2, 1, 1)   // 1 project
    def event3 = startConfigureProject(3, 2)
    def event4 = startCalculateTaskGraph(4, 1)       // fires after project config

    when:
    renderer.onOutput(event1)
    renderer.onOutput(event2)
    renderer.onOutput(event3)
    renderer.onOutput(complete(3))                   // project done → would be 100%
    renderer.onOutput(event4)                        // task graph starts → total becomes 2
    renderer.onOutput(updateNow())

    then:
    statusBar.display == '[###############] 50% CONFIGURING [0ms]'  // NOT 100%

    when:
    renderer.onOutput(complete(4))                   // task graph done → 100%
    renderer.onOutput(updateNow())

    then:
    statusBar.display == '[###############] 100% CONFIGURING [0ms]'
}
```

`ProgressBar` also has its own unit tests for rendering logic:

```
./gradlew :logging:test --tests "*.ProgressBarTest"
```

---

### Integration tests — verify against a real running build

`AbstractConsoleBuildPhaseFunctionalTest` runs a real Gradle build, uses a `BlockingHttpServer`
to pause execution at specific points, and polls `gradle.standardOutput` for the expected progress
string. The concrete implementations run with different console modes:

| Class | Console mode | Command |
|---|---|---|
| `RichConsoleBuildPhaseFunctionalTest` | Rich (unicode) | `./gradlew :logging:integTest --tests "*.RichConsoleBuildPhaseFunctionalTest"` |
| `AutoConsoleBuildPhaseFunctionalTest` | Auto-detected | `./gradlew :logging:integTest --tests "*.AutoConsoleBuildPhaseFunctionalTest"` |
| `VerboseConsoleBuildPhaseFunctionalTest` | Verbose | `./gradlew :logging:integTest --tests "*.VerboseConsoleBuildPhaseFunctionalTest"` |

The existing test `"shows progress bar and percent phase completion"` already has a blocking
call **inside the task graph calculation** (`dependsOn { server.callFromBuild('task-graph'); null }`)
and currently asserts `"100% CONFIGURING"` at that point:

```groovy
// AbstractConsoleBuildPhaseFunctionalTest.groovy:101-103 — currently asserts 100%
taskGraph.waitForAllPendingCalls()
assertHasBuildPhase("100% CONFIGURING")   // ← this assertion must change with Fix B
taskGraph.releaseAll()
```

With Fix B implemented, this assertion should change to something less than 100% (e.g. `"80% CONFIGURING"`
for a 4-project build with 1 extra task-graph slot). This existing test is the **integration-level
acceptance test** for Fix B — it already blocks inside task graph calculation, it just asserts the
wrong value right now.

Run all build-phase integration tests:

```
./gradlew :logging:integTest --tests "*.ConsoleBuildPhaseFunctionalTest"
```

---

## Recommended Approach for Mob Session

Start with **Fix B** (add `CALCULATE_TASK_GRAPH` category) as it is the most targeted, accurate fix
for the non-CC case. It directly addresses why the build appears stuck after "Configuring 100%" without
changing visible phase labels.

For the CC case (issue #29941), **Fix D** is the right direction but requires more investigation of the
CC serialization path to know how many units of work to report upfront.

**Fix A** (99% cap) is the safest short-term option if the mob wants a quick win without risk.

projects config -> 90 -> task graph -> 95 -> cc load -> 100
100 + extra for task gragph + extra for CC
47% -> 95 -> 100 

Can we express progress in terms of an ETA instead?


## MOB Session

### Extra phases

CONFIGURING[100%] -> DISCOVERING[3] (counter instead of percentage) -> [CACHING] -> EXECUTING[0/3]

CoD:
CONFIGURING[100%] (instant/maybe not visible) -> DISCOVERING[3] -> EXECUTING[0/3]

### Questions:
Can we express progress in terms of an ETA instead?

### Other ideas
projects config -> 90 -> task graph -> 95 -> cc load -> 100
100 + extra for task gragph + extra for CC
47% -> 95 -> 100

Can we express progress in terms of an ETA instead?
