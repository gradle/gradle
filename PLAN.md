# Plan: Add DISCOVERING Build Phase

## Context

After `CONFIGURING` reaches 100%, the build appears stuck while task graph calculation and CC serialization run. This is confusing to users. We introduce a new `DISCOVERING` phase between `CONFIGURING` and `EXECUTING` that shows an animated spinner with a monotonically increasing counter of discovered work items (tasks + transforms).

### Target Display

A filled segment bounces back and forth across the progress bar. The bar prefix/suffix (`│` or `[]`) stays consistent with other phases. The discovered item count replaces the percentage.

**Unicode terminal (Linux/macOS):**
```
│██████████·····│ 100% CONFIGURING [1s]     ← configuring complete
│██████████·····│ DISCOVERING [2s]           ← phase starts, segment sliding right
│·██████████····│ 42 DISCOVERING [2s]        ← 42 work items discovered
│····██████████·│ 42 DISCOVERING [3s]        ← segment keeps sliding right
│·····██████████│ 127 DISCOVERING [3s]       ← hits right edge
│····██████████·│ 127 DISCOVERING [4s]       ← bounces back left
│·██████████····│ 127 DISCOVERING [4s]       ← continues left
│██████████·····│ 127 DISCOVERING [5s]       ← hits left edge, bounces right again
│···············│ 0% EXECUTING [5s]          ← execution begins
```

**ASCII terminal (fallback):**
```
[###############] 100% CONFIGURING [1s]
[##########.....] DISCOVERING [2s]
[.##########....] 42 DISCOVERING [3s]
[.....##########] 127 DISCOVERING [3s]
[..##########...] 127 DISCOVERING [4s]
[...............] 0% EXECUTING [5s]
```

**Configure-on-Demand:**
```
│···············│ 0% CONFIGURING [0s]        ← instant, likely never renders
│██████████·····│ DISCOVERING [2s]           ← immediately visible
│···██████████··│ 42 DISCOVERING [4s]        ← projects configured on-demand inside
│···············│ 0% EXECUTING [4s]
```

**Configuration Cache hit:**
```
│██████████·····│ 100% CONFIGURING [0s]      ← instant (from cache)
│·██████████····│ DISCOVERING [1s]           ← CC load in progress
│····██████████·│ 127 DISCOVERING [2s]       ← all items loaded from cache at once
│···············│ 0% EXECUTING [2s]
```

### Bouncing Segment Animation

A filled segment (10 chars wide) bounces back and forth across the 15-char bar. Driven by `UpdateNowEvent` refresh (~100ms):

```
frame 0:  ██████████·····    → sliding right
frame 1:  ·██████████····
frame 2:  ··██████████···
frame 3:  ···██████████··
frame 4:  ····██████████·
frame 5:  ·····██████████    hits right edge
frame 6:  ····██████████·    ← bouncing left
frame 7:  ···██████████··
frame 8:  ··██████████···
frame 9:  ·██████████····
frame 0:  ██████████·····    hits left edge, cycle repeats (10 frames = ~1s)
```

Unicode mode uses `█` (full block) for the filled segment and `·` for empty. ASCII mode uses `#` and `.`.

## Scope

- Covers task graph calculation AND Configuration Cache store/load
- Counts all public nodes (tasks + transforms) via `isPublicNode()`
- Indeterminate spinner (no progress bar, no percentage)
- CACHING phase is NOT in scope
- EXECUTING display stays as-is (percentage)
- For Configure-on-Demand: DISCOVERING starts immediately after CONFIGURING (which is instant)

## Implementation Steps

### Step 1: Add `DISCOVER_WORK` to `BuildOperationCategory`

**File:** `platforms/core-runtime/base-services/src/main/java/org/gradle/internal/operations/BuildOperationCategory.java`

Add new enum value (between `CONFIGURE_PROJECT` and `RUN_MAIN_TASKS`):
```java
/**
 * Discover work items (tasks and transforms) to execute. Covers task graph calculation and configuration cache operations.
 */
DISCOVER_WORK(false, false, false),
```

### Step 2: Add indeterminate rendering mode to `ProgressBar`

**File:** `platforms/core-runtime/logging/src/main/java/org/gradle/internal/logging/console/ProgressBar.java`

Changes:
- Add `boolean indeterminate` field
- Add `static ProgressBar createIndeterminateProgressBar(ConsoleMetaData, String suffix)` factory — sets `total=1` (avoid division by zero), `indeterminate=true`
- Add `void setCount(int count)` method — sets `current = count`, clears cached `formatted`
- Add `boolean isIndeterminate()` getter
- In `formatProgress()`, short-circuit to `formatIndeterminate(...)` when `indeterminate == true` (MUST happen before any `current/total` division)
- `formatIndeterminate(timerEnabled, elapsedTime, elapsedTimeStr)`:
  - Bouncing segment: 10-char filled block slides across 15-char bar, bouncing at edges
  - Segment position driven by `elapsedTime / 100`, 10 frames per cycle (~1s bounce)
  - Reuses existing `progressBarPrefix`/`progressBarSuffix` (`│` or `[]`)
  - Unicode: `█` for filled, `·` for empty. ASCII: `#` for filled, `.` for empty
  - Status after bar: `<count> DISCOVERING [<time>]` (omit count when 0)
  - Always recompute (no caching — animation changes every 100ms)
  - Taskbar: use state 3 (indeterminate) via `buildOsc94Sequence("3")`

### Step 3: Add `Phase.Discovering` to `BuildStatusRenderer`

**File:** `platforms/core-runtime/logging/src/main/java/org/gradle/internal/logging/console/BuildStatusRenderer.java`

Changes:
- Add `Discovering` to `Phase` enum: `Initializing, Configuring, Discovering, Executing`
- Add `int discoveringWorkItemCount` field
- In `onOutput()`, add handler for `DISCOVER_WORK` category (see Step 5 for the full handler that distinguishes top-level vs nested events)
- Modify `phaseStarted()` to create indeterminate progress bar for Discovering:
  ```java
  if (phase == Phase.Discovering) {
      progressBar = ProgressBar.createIndeterminateProgressBar(consoleMetaData, "DISCOVERING");
  } else {
      progressBar = ProgressBar.createProgressBar(consoleMetaData, phase.name().toUpperCase(Locale.ROOT), totalProgress);
  }
  ```

### Step 4: Fire `DISCOVER_WORK` build operation in work controllers

Both controllers need `BuildOperationRunner` injected (already available in their factories).

**File:** `platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/VintageBuildTreeWorkController.kt`

Wrap `scheduleRequestedTasks` in a `DISCOVER_WORK` build operation:
```kotlin
class VintageBuildTreeWorkController(
    private val workPreparer: BuildTreeWorkPreparer,
    private val workExecutor: BuildTreeWorkExecutor,
    private val taskGraph: BuildTreeWorkGraphController,
    private val buildOperationRunner: BuildOperationRunner  // NEW
) : BuildTreeWorkController {

    override fun scheduleAndRunRequestedTasks(taskSelector: EntryTaskSelector?): TaskRunResult {
        return taskGraph.withNewWorkGraph { graph ->
            val finalizedGraph = Try.ofFailable {
                buildOperationRunner.call(object : CallableBuildOperation<BuildTreeWorkGraph.FinalizedGraph>() {
                    override fun call(context: BuildOperationContext) =
                        workPreparer.scheduleRequestedTasks(graph, taskSelector)
                    override fun description() =
                        BuildOperationDescriptor.displayName("Discover work for build tree")
                            .metadata(BuildOperationCategory.DISCOVER_WORK)
                })
            }
            if (finalizedGraph.isSuccessful) {
                TaskRunResult.ofExecutionResult(workExecutor.execute(finalizedGraph.get()))
            } else {
                TaskRunResult.ofScheduleFailure(finalizedGraph.failure.get())
            }
        }
    }
}
```

**File:** `platforms/core-configuration/configuration-cache/src/main/kotlin/org/gradle/internal/cc/impl/ConfigurationCacheAwareBuildTreeWorkController.kt`

Wrap `loadOrScheduleRequestedTasks` in a `DISCOVER_WORK` build operation. This covers:
- CC miss: task graph calculation + CC store
- CC hit: CC load
```kotlin
val result = Try.ofFailable {
    buildOperationRunner.call(object : CallableBuildOperation<...>() {
        override fun call(context: BuildOperationContext) =
            cache.loadOrScheduleRequestedTasks(graph, graphBuilder) {
                workPreparer.scheduleRequestedTasks(graph, taskSelector)
            }
        override fun description() =
            BuildOperationDescriptor.displayName("Discover work for build tree")
                .metadata(BuildOperationCategory.DISCOVER_WORK)
    })
}
```

**Note:** CC miss has a store-then-reload path. The first `DISCOVER_WORK` wraps the initial scheduling + store. The reload at line 100-103 happens in a second `withNewWorkGraph` — we should wrap that in a second `DISCOVER_WORK` operation too. `BuildStatusRenderer` will just re-enter Discovering phase (counter resets — acceptable since it's a reload).

**Factory wiring:**

- `VintageBuildTreeLifecycleControllerFactory.kt`: pass `buildOperationRunner` to `VintageBuildTreeWorkController`
- `ConfigurationCacheBuildTreeLifecycleControllerFactory.kt`: pass `buildOperationRunner` to `ConfigurationCacheAwareBuildTreeWorkController`

### Step 5: Work item counting via child build operations

**Constraint:** `ProgressEvent` (from `context.progress()`) is filtered out by `GroupingProgressLogEventGenerator` and never reaches `BuildStatusRenderer`. So we use short-lived child build operations instead.

**File:** `subprojects/core/src/main/java/org/gradle/internal/build/BuildOperationFiringBuildWorkPreparer.java`

After `populateTaskGraph()` completes in `PopulateWorkGraph.run()`, fire a child build operation that carries the discovered node count:

```java
@Override
public void run(BuildOperationContext buildOperationContext) {
    populateTaskGraph();

    // Report discovered work items for progress display
    int publicNodeCount = plan.getContents().size();
    buildOperationRunner.run(new RunnableBuildOperation() {
        @Override
        public void run(BuildOperationContext ctx) {}
        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Discovered " + publicNodeCount + " work items")
                .metadata(BuildOperationCategory.DISCOVER_WORK)
                .totalProgress(publicNodeCount);
        }
    });

    // ... existing result-setting code
}
```

This fires a `ProgressStartEvent` with `category=DISCOVER_WORK` and `totalProgress=publicNodeCount`, immediately followed by `ProgressCompleteEvent`.

**In `BuildStatusRenderer`**, handle nested `DISCOVER_WORK` events during Discovering phase:

```java
} else if (startEvent.getBuildOperationCategory() == BuildOperationCategory.DISCOVER_WORK) {
    if (currentPhase == Phase.Discovering) {
        // Nested DISCOVER_WORK event: carries work item count in totalProgress
        discoveringWorkItemCount += startEvent.getTotalProgress();
        if (progressBar != null) {
            progressBar.setCount(discoveringWorkItemCount);
        }
    } else {
        // Top-level DISCOVER_WORK: start the Discovering phase
        discoveringWorkItemCount = 0;
        phaseStarted(startEvent, Phase.Discovering);
    }
}
```

The first `DISCOVER_WORK` (from the work controller) starts the phase. Nested `DISCOVER_WORK` events (from per-build task graph ops) carry counts via `totalProgress`. The counter accumulates across all per-build calculations (composite builds).

### Step 6: Configure-on-Demand handling

No special handling needed. With CoD:
1. `CONFIGURE_ROOT_BUILD` fires → Configuring phase (instant, likely never renders at 100ms refresh)
2. `DISCOVER_WORK` fires → Discovering phase starts immediately
3. `CONFIGURE_PROJECT` events fire inside Discovering (ignored — `currentPhase != Configuring`)
4. Task graph completes → work items counted
5. `RUN_MAIN_TASKS` fires → Executing phase

The brief Configuring flash is sub-millisecond and won't render.

### Step 7: Update tests

**Unit tests — `BuildStatusRendererTest.groovy`:**
- Add test: Discovering phase follows Configuring
- Add test: Discovering phase shows spinner (no percentage)
- Add test: Nested DISCOVER_WORK events increment counter
- Add test: Discovering transitions to Executing

**Unit tests — `ProgressBarTest.groovy`:**
- Add test: indeterminate mode renders spinner + counter
- Add test: spinner animates with elapsed time
- Add test: `setCount()` updates display
- Add test: no division-by-zero in indeterminate mode

**Integration tests — `AbstractConsoleBuildPhaseFunctionalTest.groovy`:**
- Update existing task-graph blocking test: assert `DISCOVERING` instead of `100% CONFIGURING`
- Update `regexFor()` to handle indeterminate format: `. DISCOVERING \[\d+\] \[[\dms ]+\]`
- Add test: CoD shows DISCOVERING (not stuck at CONFIGURING)

## Files Modified (ordered by implementation sequence)

| # | File | Change |
|---|------|--------|
| 1 | `platforms/core-runtime/base-services/.../BuildOperationCategory.java` | Add `DISCOVER_WORK` |
| 2 | `platforms/core-runtime/logging/.../ProgressBar.java` | Add indeterminate mode (spinner + counter) |
| 3 | `platforms/core-runtime/logging/.../BuildStatusRenderer.java` | Add Discovering phase + counter tracking |
| 4 | `platforms/core-configuration/configuration-cache/.../VintageBuildTreeWorkController.kt` | Wrap scheduling in DISCOVER_WORK op |
| 5 | `platforms/core-configuration/configuration-cache/.../VintageBuildTreeLifecycleControllerFactory.kt` | Inject buildOperationRunner |
| 6 | `platforms/core-configuration/configuration-cache/.../ConfigurationCacheAwareBuildTreeWorkController.kt` | Wrap loadOrSchedule in DISCOVER_WORK op |
| 7 | `platforms/core-configuration/configuration-cache/.../ConfigurationCacheBuildTreeLifecycleControllerFactory.kt` | Inject buildOperationRunner |
| 8 | `subprojects/core/.../BuildOperationFiringBuildWorkPreparer.java` | Fire child DISCOVER_WORK with node count |
| 9 | `platforms/core-runtime/logging/src/test/.../BuildStatusRendererTest.groovy` | Add Discovering phase tests |
| 10 | `platforms/core-runtime/logging/src/test/.../ProgressBarTest.groovy` | Add indeterminate mode tests |
| 11 | `platforms/core-runtime/logging/src/integTest/.../AbstractConsoleBuildPhaseFunctionalTest.groovy` | Update phase assertions |

## Verification

1. **Unit tests:** `./gradlew :logging:test --tests "*.BuildStatusRendererTest" --tests "*.ProgressBarTest"`
2. **Integration tests:** `./gradlew :logging:integTest --tests "*.ConsoleBuildPhaseFunctionalTest"`
3. **Manual verification:** Run a multi-project build with `--console=rich` and observe the DISCOVERING phase between CONFIGURING and EXECUTING
4. **CoD verification:** Run with `--configure-on-demand --console=rich` and verify DISCOVERING appears immediately
5. **CC verification:** Run twice (first miss, then hit) with `--configuration-cache --console=rich` and verify DISCOVERING appears in both cases

## Risks / Open Questions

- **CC miss store-then-reload:** Two DISCOVER_WORK operations fire. The counter resets on reload. This is acceptable for v1 but could be refined.
- **Reusing `DISCOVER_WORK` category for both phase-level and counting events** relies on `BuildStatusRenderer` distinguishing top-level vs nested by checking `currentPhase`. This is fragile — if ordering changes, it could misinterpret. Alternative: add a separate `WORK_ITEMS_DISCOVERED` category for counting only. Worth discussing.
