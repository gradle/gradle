# Parallel Task Dependency Resolution for Isolated Projects

## How Task Scheduling Works

```
./gradlew compileJava
        │
        ▼
┌─────────────────────────┐
│  Task Selection         │  "compileJava" → [:app:compileJava, :lib:compileJava]
│                         │  (triggers project configuration in IP mode)
└─────────┬───────────────┘
          │  addEntryTasks([:app:compileJava, :lib:compileJava])
          ▼
┌─────────────────────────┐
│  Discover Relationships │  Sequential DFS — resolves dependencies
│                         │  one node at a time, walks the full graph
└─────────┬───────────────┘
          ▼
┌─────────────────────────┐
│  Determine Plan         │  Topological sort → ordered execution list
└─────────┬───────────────┘
          ▼
┌─────────────────────────┐
│  CC Store/Load          │  Serialize the work graph to cache
└─────────┬───────────────┘  (on CC hit, everything above is skipped)
          ▼
┌─────────────────────────┐ʔ„
│  Execute                │  Worker threads pick up tasks
└─────────────────────────┘
```

### The bottleneck

**Discover Relationships** is single-threaded. Each node is resolved sequentially,
acquiring one project lock at a time. In large builds (1000+ projects),
this phase dominates CC-miss builds where configuration is already done.

### How dependencies are discovered

Task dependencies are discovered by walking dependency containers (`TaskDependency`,
`FileCollection`, `Provider` chains). The walker returns leaf nodes:

- **String task paths** (`dependsOn ':foo:compile'`) — resolved via `TaskResolver`
- **Artifact dependencies** (`implementation project(':lib')`) — resolved through the
  dependency engine: `compileClasspath` → `ProjectArtifactResolver` → `DefaultResolvableArtifact`
  → `ResolveAction` (a `WorkNodeAction`) → `ActionNode`
- **Reverse dependency searches** (`buildDependents`) — search all projects for dependents

## Parallel Task Dependency Resolution (BFS Waves)

We replace the sequential DFS with a parallel BFS for `LocalTaskNode` resolution:

```
Entry tasks: [:app:compileJava, :lib:compileJava]
                    │
                    ▼
┌──────────────────────────────────────────────────────┐
│  Wave 1 — resolve in parallel, grouped by project    │
│                                                      │
│   :app lock          :lib lock                       │
│  ┌────────────┐    ┌────────────────┐                │
│  │:app:compile│    │:lib:compileJava│                │
│  │  Java      │    │  → (done)      │                │
│  │  → Action  │    └────────────────┘                │
│  │    Node    │                                      │
│  │  → :lib    │ ← cross-project dep deferred         │
│  │    :jar    │    as placeholder                     │
│  └────────────┘                                      │
└──────────────────┬───────────────────────────────────┘
                   │  resolve placeholders:
                   │  :lib:jar found under :lib lock
                   ▼
┌──────────────────────────────────────────────────────┐
│  Wave 2 — newly discovered LocalTaskNodes            │
│                                                      │
│   :lib lock                                          │
│  ┌────────────┐                                      │
│  │ :lib:jar   │                                      │
│  │  → :lib:   │                                      │
│  │  compile   │                                      │
│  │  Java      │  ← already resolved in wave 1        │
│  └────────────┘                                      │
└──────────────────┬───────────────────────────────────┘
                   │  no more LocalTaskNodes
                   ▼
┌──────────────────────────────────────────────────────┐
│  Sequential DFS — handles remaining non-task nodes   │
│  (ActionNodes, TaskInAnotherBuild, etc.)             │
│  Pre-resolved tasks use cached results → fast        │
└──────────────────────────────────────────────────────┘
```

### Key ideas

- **BFS waves** resolve `LocalTaskNode`s in parallel, one project lock at a time
- **Cross-project dependencies** → deferred as placeholders, resolved between waves under the correct project lock
- **Non-task nodes** (ActionNode, etc.) → sequential DFS fallback, but pre-resolved tasks use cached results
- **Same result** as sequential, just faster discovery

### What's parallelized vs sequential

| Phase | Before | After |
|---|---|---|
| Task dependency resolution | Sequential DFS, one node at a time | Parallel BFS waves by project |
| Cross-project task lookup | Inline under single lock | Deferred placeholder → resolved under correct lock |
| ActionNode / transforms | Sequential | Sequential (TODO: resolve between waves to discover more tasks for parallel waves) |
| Execution plan ordering | Sequential | Sequential (unchanged) |
| Task execution | Parallel (unchanged) | Parallel (unchanged) |

## Future: IP Cache (Per-project CC Store)

The parallel BFS creates a natural per-project serialization boundary. Each project's
`Map<LocalTaskNode, ResolvedNodeRelationships>` is self-contained, with cross-project
edges stored as symbolic references (`DeferredCrossProjectDependency`: identity path + task name).

This enables **per-project configuration cache**: change one project's build script and
only that project needs re-resolution. The rest load from cache.

```
CC hit (whole graph)  →  skip straight to Execute

CC miss (whole graph), but per-project hits:

  ┌─────────────────────────┐
  │  Task Selection         │
  └─────────┬───────────────┘
            ▼
  ┌─────────────────────────────────────────┐
  │  BFS                                    │
  │  :app → per-project CC hit, load chunk  │
  │  :lib → per-project CC miss, resolve    │
  └─────────┬───────────────────────────────┘
            ▼
  ┌───────────────────────────────────┐
  │  IP cache (Per-project CC store)  │  store :lib's chunk
  └─────────┬─────────────────────────┘
            ▼
  ┌──────────────────────────┐
  │  Stitch + Determine Plan │  resolve cross-project placeholders, topological sort
  └─────────┬────────────────┘  
            ▼
  ┌─────────────────────────┐
  │  CC Load/Store          │  store everything (existing behavior)
  └─────────┬───────────────┘
            ▼
  ┌─────────────────────────┐
  │  Execute                │
  └─────────────────────────┘
```

The whole-graph CC is the fast path — when it hits, nothing else runs.
Per-project CC is the fallback that makes the miss path faster: only
cache-miss projects need re-resolution, the rest load from per-project cache.
Both stored, cheapest one wins on the next run.
