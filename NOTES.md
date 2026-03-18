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

## Recommended Approach for Mob Session

Start with **Fix B** (add `CALCULATE_TASK_GRAPH` category) as it is the most targeted, accurate fix
for the non-CC case. It directly addresses why the build appears stuck after "Configuring 100%" without
changing visible phase labels.

For the CC case (issue #29941), **Fix D** is the right direction but requires more investigation of the
CC serialization path to know how many units of work to report upfront.

**Fix A** (99% cap) is the safest short-term option if the mob wants a quick win without risk.
