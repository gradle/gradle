# Daemon-Side Test Discovery

## Goal

Move JUnit Platform test discovery out of the forked worker JVM and into the Gradle daemon.
Today the daemon scans class files by name or bytecode to find test *classes*, then sends
whole classes to the worker where JUnit Platform does the real discovery and execution in one
shot.  This experiment makes the daemon call `Launcher.discover()` itself, producing a
`TestPlan` of all tests, then distributes them to workers via a configurable
`TestDistributionStrategy`.

### Why this matters

- **Smarter filtering**: the daemon knows every test method *before* forking, so `--tests`
  filtering, engine filters, and tag filters are applied before serialization and worker forking.
- **Better sharding / parallelization**: tests can be distributed across workers with
  awareness of container cohesion requirements via `TestDistributionStrategy`.
- **Richer reporting**: test counts and structure are known before execution begins.
- **Non-class-based testing improvements**: resource-based tests (e.g., `.rbt` files) gain
  per-file worker distribution, daemon-side file-path filtering, and hierarchical reporting —
  bringing them to parity with class-based tests.

## Branch

`tt/experiments/94/client-side-test-discovery`

PR: https://github.com/gradle/gradle/pull/36195

## How to enable

```groovy
testing.suites.test {
    useJUnitJupiter()

    targets.all {
        testTask.configure {
            useDaemonSideTestDiscovery = true
            testDistributionStrategy = TestDistributionStrategy.BY_TOP_LEVEL_TEST_CONTAINER  // default
        }
    }
}
```

Both properties are `@Incubating` and declared on `AbstractTestTask`.
`useDaemonSideTestDiscovery` defaults to `false`.
`testDistributionStrategy` defaults to `BY_TOP_LEVEL_TEST_CONTAINER`.

## Benefits so far

Concrete wins from the experiment in its current state:

- **Improved Non-Class-Based Test Parallelism.** Resource-based tests are now distributed
  across workers the same way class-based tests are, rather than executing as a single
  monolithic batch per definitions directory.  `resource-based tests are distributed across
  workers` confirms the per-strategy PID counts (3 PIDs under `BY_TOP_LEVEL_TEST_CONTAINER`,
  4 PIDs under `BY_INDIVIDUAL_TEST`).
- **Improved Non-Class-Based Test Filtering.** File-based filters (`includeTestsMatching`,
  `excludeTestsMatching`, `--tests`) are evaluated in the daemon against discovered
  `TestIdentifier`s dispatching `FileSource` to `FileTestSelectionMatcher`, so excluded
  resource definitions never reach a worker — verified by `daemon-side discovery with
  file-based filter includes only matching definitions`.
- **Filtering moves to the daemon.** Tag filters, engine filters, `--tests` name filters, and
  file-path filters are all applied before any worker is forked.  Excluded tests never reach a
  worker — demonstrated by `excludeTestsMatching filters classes with daemon-side discovery` and
  `daemon-side discovery with file-based filter includes only matching definitions`.
- **Cohesion-preserving test distribution.** `BY_TOP_LEVEL_TEST_CONTAINER` keeps all tests from
  one class in one worker, so `@BeforeAll`/`@AfterAll`, `@Stepwise`, `@Nested` outer state, and
  shared statics behave correctly even with many workers.  `BY_INDIVIDUAL_TEST` exists as an
  opt-in for fully-independent tests — the contrast is measured quantitatively in
  `TestCohesionIntegrationTest`.
- **Richer file-based test reporting.** Resource-based tests now report as a two-level tree
  (`:SomeTestSpec.rbt:foo`) with the file as a distinct container, replacing the old flat
  display-name concat (`:SomeTestSpec.rbt - foo`).  Sibling directories with same-named
  definition files produce distinct paths (`:alpha/SameName.rbt` vs `:beta/SameName.rbt`).
- **Unified worker-side re-discovery via `selectUniqueId`.** One code path handles class-based
  engines (Jupiter/Spock/Vintage) and resource-based engines, because both produce
  `TestIdentifier`s whose unique IDs fully encode the test's provenance.
- **Previously-failed prioritization at the daemon level.** `RunPreviousFailedFirstTestDefinitionProcessor`
  now sees every test before forking, so failing classes genuinely run first rather than
  merely being emitted first from the detector.
- **Zero impact when disabled.** `useDaemonSideTestDiscovery` defaults to `false`; the legacy
  bytecode/filename scan path is unchanged.  Rollout risk is opt-in only.

## Architecture

```
DAEMON JVM                                       WORKER JVM (forked)
───────────────────────────────────────────      ──────────────────────────────────────
AbstractTestTask
  │  useDaemonSideTestDiscovery = true
  │  testDistributionStrategy = BY_TOP_LEVEL_TEST_CONTAINER
  ▼
Test.createTestExecutionSpec()
  │  → JvmTestExecutionSpec
  ▼
DefaultTestExecuter
  └─ DefaultTestDetector
       │
       │ discoveryScan()
       │  ├─ URLClassLoader(classpath + testClassesDirs)
       │  ├─ set context CL, restore after discovery
       │  ├─ LauncherFactory.create().discover(
       │  │    selectClasspathRoots + selectDirectory per testDefinitionDir)
       │  │    applies engine + tag filters from JUnitPlatformOptions
       │  └─ TestDistributor.distribute(testPlan, processor)
       │       ├─ ByTopLevelContainerTestDistributor: emits top-level containers
       │       └─ ByIndividualTestTestDistributor: emits individual leaf tests
       │
       ▼
  PatternMatchTestDefinitionProcessor
       │  matches() uses className + methodName from TestIdentifier.getSource()
       ▼
  RunPreviousFailedFirstTestDefinitionProcessor
       │  prioritizes by className from previous failures
       ▼
  ForkingTestDefinitionProcessor
       │
       │── serialize (ObjectOutputStream) ──────────▶  deserialize
       │                                               │
       │                                  JUnitPlatformTestDefinitionProcessor
       │                                    ├─ selectUniqueId per definition
       │                                    ├─ createTestPlan(launcher)
       │                                    │    applies name/engine/tag filters
       │                                    │    launcher.discover(request)
       │                                    └─ launcher.execute(plan)
```

### Test distribution strategies

| Strategy | Behavior | Cohesion |
|----------|----------|----------|
| `BY_TOP_LEVEL_TEST_CONTAINER` | Emits one `TestIdentifierTestDefinition` per top-level container (class, file, or directory). All tests from the same container go to the same worker. | Preserved |
| `BY_INDIVIDUAL_TEST` | Emits each leaf test individually. Tests from the same container may land on different workers. | Not preserved |

A **top-level container** is a `TestIdentifier` that is a direct child of an engine root.
Engine roots have no `TestSource`, so a top-level container is any identifier whose parent
has no source.  This covers:
- Class-based containers (`ClassSource`) — test classes
- File-based containers (`FileSource`) — resource-based test definition files (`.rbt`, etc.),
  represented by `TestDefinitionFileDescriptor` which groups individual tests from the same file
- Directory-based containers (`DirectorySource`) — test definition directories
- Any other source type from custom engines
- JUnit Platform Suite containers (`@Suite` + `@SelectClasses`) — the suite engine creates a
  single top-level container for the suite, with the selected classes as children.  All suite
  members are sent to the same worker, preserving the suite's grouping contract.

`BY_TOP_LEVEL_TEST_CONTAINER` is the default and the safe choice.  `BY_INDIVIDUAL_TEST` exists for
testing and for cases where all tests are fully independent with no container-level lifecycle
or shared state.

### Worker-side re-discovery

The worker always uses `selectUniqueId` to re-discover tests.  This works uniformly for:
- **Class-based engines** (JUnit Jupiter, Spock, vintage) — resolve unique IDs natively
- **Resource-based engines** — resolve unique IDs by extracting the full file path from the
  `testDefinitionFile` segment (which carries the absolute path, not just the filename)

The resource-based test engine hierarchy mirrors the class-based hierarchy:
```
engine root (rbt-engine)                       engine root (junit-jupiter)
  ├── TestDefinitionFileDescriptor (CONTAINER)   ├── test class (CONTAINER)
  │     ├── test "foo" (TEST)                    │     ├── test method (TEST)
  │     └── test "bar" (TEST)                    │     └── test method (TEST)
  └── TestDefinitionFileDescriptor (CONTAINER)   └── test class (CONTAINER)
        └── test "other" (TEST)                        └── test method (TEST)
```

The `TestDefinitionFileDescriptor` is the file-level container. Its unique ID carries the
full file path: `[engine:rbt-engine]/[testDefinitionFile:/full/path/to/Spec.rbt]`.
Individual tests are children: `[...]/[testDefinition:foo]`.

This allows the `ResourceBasedSelectorResolver` to reconstruct the file location from the
unique ID alone, without needing directory selectors or external context.

Before this change, resource-based engines produced a single flat descriptor per test whose
display name was the concatenation `"file.rbt - name"` and whose unique ID carried both
file and test segments on the same descriptor.  Splitting into a two-level descriptor tree
both gives file-based tests a proper container for reporting (`:SomeTestSpec.rbt:foo`
instead of `:SomeTestSpec.rbt - foo`) and lets the daemon distribute tests on a per-file
basis without any engine-specific handling.


## Non-class-based testing

### How it works today (standard detection)

Non-class-based tests (resource-based test engines) use a different flow from class-based tests:

1. The `Test` task is configured with `testDefinitionDirs.from("src/test/definitions")`
2. `DefaultTestDetector.filenameScan()` iterates `spec.getCandidateTestDefinitionDirs()`
3. Each directory becomes a `DirectoryBasedTestDefinition(dir)`
4. The worker receives the directory → `executeDirectory()` creates a `selectDirectory(dir)`
5. The worker's `Launcher.discover()` finds the resource-based engine, which discovers files
6. The engine groups tests under `TestDefinitionFileDescriptor` containers per file
7. The engine executes all tests from all files in that directory together

### How it works with daemon-side discovery

1. `DefaultTestDetector.scanForTestPlan()` adds `selectDirectory(dir)` for each
   `candidateTestDefinitionDir` alongside `selectClasspathRoots`
2. The daemon's `Launcher.discover()` passes directory selectors to all engines, including
   resource-based engines which discover `.rbt` files and group tests under file containers
3. `ByTopLevelContainerTestDistributor` collects top-level containers — both class-sourced
   (from Jupiter) and file-sourced (from resource-based engines)
4. The worker receives `TestIdentifierTestDefinition`s and uses `selectUniqueId` for all of
   them — resource-based engines resolve the unique ID by extracting the full file path from
   the `testDefinitionFile` segment, then re-discover the file's tests
5. The engine executes all tests from the re-discovered file

Daemon-side filtering for file-based tests works via `TestIdentifierTestDefinition.matches()`,
which dispatches `FileSource` identifiers to `FileTestSelectionMatcher.matchesFile()`.  The
matcher resolves roots via `toRealPath()` at construction to handle Unicode path normalization
(e.g., NFD/NFC mismatch on macOS).

## Key types

### Public API (`testing-base`)

| Type | Purpose |
|------|---------|
| `AbstractTestTask.getUseDaemonSideTestDiscovery()` | `@Incubating` `Property<Boolean>`, convention `false` |
| `AbstractTestTask.getTestDistributionStrategy()` | `@Incubating` `Property<TestDistributionStrategy>`, convention `BY_TOP_LEVEL_TEST_CONTAINER` |
| `TestDistributionStrategy` | Enum: `BY_TOP_LEVEL_TEST_CONTAINER`, `BY_INDIVIDUAL_TEST` |

### Internal API

| Type | Module | Purpose |
|------|--------|---------|
| `TestIdentifierTestDefinition` | `testing-base-infrastructure` | `TestDefinition` wrapping a JUnit `TestIdentifier`. Extracts `className`/`methodName`/`file` on demand from `TestSource` for filtering and prioritization. |
| `TestDistributor` | `testing-jvm` (detection.distribution) | Interface: `distribute(TestPlan, TestDefinitionProcessor)` → `List<List<TestDefinition>>` |
| `ByTopLevelContainerTestDistributor` | `testing-jvm` (detection.distribution) | Groups by top-level container (class, file, or directory). Preserves cohesion. |
| `ByIndividualTestTestDistributor` | `testing-jvm` (detection.distribution) | Emits each leaf test individually. No grouping. |
| `DefaultTestDetector` | `testing-jvm` (detection) | Selects distributor based on `TestDistributionStrategy`. Adds `selectDirectory` for `testDefinitionDirs`. |
| `UnsupportedTestDiscoveryException` | `testing-jvm` (detection) | Thrown when daemon discovery is enabled with non-JUnit-Platform framework. Implements `ResolutionProvider`. |
| `PatternMatchTestDefinitionProcessor` | `testing-base` | Now accepts `testDefinitionDirs` for file-path matching. Passes roots to `TestSelectionMatcher`. |
| `FileTestSelectionMatcher` | `testing-base-infrastructure` | Resolves roots via `toRealPath()` at construction to match canonicalized file paths. |

### Test fixture types

| Type | Purpose |
|------|---------|
| `TestDefinitionFileDescriptor` | `CONTAINER` descriptor grouping all tests from one `.rbt` file. Carries `FileSource` and encodes the full file path in the unique ID's `testDefinitionFile` segment. |
| `ResourceBasedTestDescriptor` | Leaf `TEST` descriptor for an individual test within a file. Child of `TestDefinitionFileDescriptor`. Display name is just the test name (e.g., `foo`), since the parent container identifies the file. |
| `ResourceBasedSelectorResolver` | Handles `DirectorySelector`, `FileSelector`, and `UniqueIdSelector`. Creates `TestDefinitionFileDescriptor` + child `ResourceBasedTestDescriptor`s for each resolved file. |

## Test coverage

### `DaemonSideTestDiscoveryIntegrationTest`

| Test | What it verifies |
|------|------------------|
| `can run tests with daemon-side discovery` | Basic JUnit Jupiter discovery + `--tests` wildcard filtering |
| `fails with JUnit 4 when daemon-side discovery is enabled` | `useJUnit()` throws `UnsupportedTestDiscoveryException` with resolutions |
| `applies tag filters during daemon-side discovery` | `includeTags` applied at daemon-side discovery |
| `filters tests by name in the daemon` | `--tests` fully qualified class filter works |
| `runs previously failed tests first` | Previously-failed class prioritization across runs |
| `discovers parameterized tests` | `@ParameterizedTest` with `@ValueSource` |
| `discovers nested test classes` | `@Nested` inner class discovered alongside outer method |
| `handles disabled tests` | `@Disabled` methods skipped, enabled methods executed |
| `succeeds with empty test suite when failOnNoDiscoveredTests is false` | No tests discovered, no failure with `failOnNoDiscoveredTests = false` |
| `discovers JUnit 4 tests via vintage engine` | Mixed JUnit 4 + Jupiter via vintage engine |
| `discovers Spock Stepwise tests` | Spock `@Stepwise` spec with 5 ordered methods |
| `fails with TestNG when daemon-side discovery is enabled` | `useTestNG()` throws `UnsupportedTestDiscoveryException` with resolutions |
| `filters individual methods within a matching class` | `--tests ClassName.method` with daemon discovery: daemon passes class, worker filters to specific method |
| `JUnit Platform suite keeps all suite classes in the same worker` | `@Suite` + `@SelectClasses` with `maxParallelForks=4`: suite is one top-level container, both members run in the same PID |
| `excludeTestsMatching filters classes with daemon-side discovery` | `excludeTestsMatching` excludes a class at daemon level — excluded class never reaches worker |
| `class with all methods filtered is still sent to worker` | `--tests ClassName.method` with 2 methods: daemon sends class (optimistic `mayIncludeClass`), worker filters to specific method. Unrelated class excluded at daemon level. |

### `TestCohesionIntegrationTest`

Each test parameterized on daemon discovery with `BY_TOP_LEVEL_TEST_CONTAINER` and
`BY_INDIVIDUAL_TEST`. All run with `maxParallelForks = 4`. (Standard detection is covered
elsewhere and would duplicate `BY_TOP_LEVEL_TEST_CONTAINER` behavior here.)

| Test | BY_TOP_LEVEL_TEST_CONTAINER | BY_INDIVIDUAL_TEST |
|------|----------------------|-------------------|
| `BeforeAll and AfterAll run exactly once per class` | Pass | Broken (lifecycle duplicated) |
| `shared mutable static state is consistent` | Pass | Broken (state reset per worker) |
| `Spock Stepwise tests run in declaration order` | Pass | Broken (steps duplicated) |
| `nested classes share outer BeforeEach` | Pass | Pass |
| `TestFactory dynamic tests stay in a single worker` | Pass | Pass |
| `parameterized tests share class BeforeAll` | Pass | Pass |
| `repeated tests share class lifecycle` | Pass | Pass |

### `ByTopLevelContainerTestDistributorTest`

Unit tests with real `TestPlan` built from `AbstractTestDescriptor`:
- Two classes distributed as separate units
- Nested class not distributed separately
- Class with only parameterized methods distributed as a single unit
- Empty plan produces no distributions
- Methods from different classes never grouped
- 5 `isTopLevelContainer` edge cases (ClassSource top-level, MethodSource rejected, nested class rejected, engine root rejected, container without source rejected)

### `NonClassBasedTestingIntegrationTest`

| Test | What it verifies |
|------|------------------|
| `resource-based tests in multiple directories are discovered and executed with daemon-side discovery` | 2 directories, 3 `.rbt` files, 6 tests — all discovered and executed |
| `resource-based tests are distributed across workers with daemon-side discovery (#strategy)` | 3 dirs, 3 files, `maxParallelForks=4`. Parameterized on `BY_TOP_LEVEL_TEST_CONTAINER` (3 PIDs) and `BY_INDIVIDUAL_TEST` (4 PIDs) |
| `daemon-side discovery with file-based filter includes only matching definitions` | `includeTestsMatching "*AlphaSpec*"` with 3 `.rbt` files — only AlphaSpec's 2 tests execute, 1 worker used |
| `same-named definition files in sibling directories produce distinct test paths` | `alpha/SameName.rbt` and `beta/SameName.rbt` produce distinct `:alpha/SameName.rbt:alphaTest` and `:beta/SameName.rbt:betaTest` paths — file identity uses the path relative to the test definitions root, not just the file name |

### Potential additional tests

- Mixed class + resource filtering: `--tests` pattern matching both a Jupiter class and an
  `.rbt` file in the same build, with daemon discovery enabled
- `excludeTestsMatching` on resource-based tests with daemon discovery
- Engine filter (`excludeEngines("rbt-engine")`) with mixed class + resource tests
- `--tests` command-line filter on resource-based tests with daemon discovery (vs `filter {}` block)
- **Performance baseline / metaspace impact benchmark**: measure daemon discovery time vs
  worker-side detection, and track metaspace growth across repeated `gradle test` invocations
  on a long-lived daemon to quantify the `URLClassLoader` accumulation risk
- **Develocity / build scan event visibility**: verify that daemon-side discovery events
  produce correct build scan timelines — that discovery time is attributed to the daemon,
  not to workers, and that test counts reported to Develocity match the distributed plan

## Dependency changes

| Module | Dependency | Change | Reason |
|--------|-----------|--------|--------|
| `testing-jvm` | `libs.guava` | `implementation` → `api` | Used in public API of `DefaultTestDetector` (`ImmutableList`) |
| `testing-jvm` | `providedLibs.junitPlatform` | Added as `api` | `TestDistributor.distribute()` exposes `TestPlan` in method signature |
| `testing-jvm` | `providedLibs.junitPlatformEngine` | Added as `implementation` | `ByTopLevelContainerTestDistributor` uses `ClassSource` from engine module |
| `testing-base-infrastructure` | `providedLibs.junitPlatform` | Added as `api` | `TestIdentifierTestDefinition` exposes `TestIdentifier` |
| `testing-base-infrastructure` | `providedLibs.junitPlatformEngine` | Added as `implementation` | `TestIdentifierTestDefinition` uses `MethodSource`/`ClassSource` |

## Potential Risks

### Double discovery

The daemon discovers every test and produces a `TestPlan`, but the worker's `Launcher` refuses
to execute a `TestPlan` it didn't create (`PreconditionViolationException`).  The worker must
re-discover using `selectUniqueId` selectors.  Every test is discovered twice — once in the
daemon and once in the worker.  This is a JUnit Platform API constraint.

### JUnit Platform on the Gradle API classpath

The `testing-jvm` and `testing-base-infrastructure` modules declare `providedLibs.junitPlatform`
as `api` dependencies.  This is required because JUnit Platform types (`TestPlan`, `TestIdentifier`)
appear in the method signatures of `TestDistributor.distribute()` and
`TestIdentifierTestDefinition`.  While these are internal API classes (in `internal` packages),
the dependency health checker enforces that types visible in public method signatures must be
`api` scope.  This means JUnit Platform classes are on the compile classpath of downstream
modules, creating a tighter coupling to JUnit Platform than existed before daemon-side discovery.

### `URLClassLoader` in the daemon

#### How this differs from the old path

In the old (non-daemon) detection path, test class loading happens exclusively in the forked
worker JVM — a short-lived process where all classes, static state, and native libraries are
destroyed on exit.  The daemon never touches the test classpath:
- `filenameScan()` reads `.class` file names from the filesystem without loading classes
- `detectionScan()` uses bytecode analysis (ASM) without `Class.forName`

Daemon-side discovery changes this: `discoveryScan()` creates a `URLClassLoader` over the
test classpath and calls `LauncherFactory.create().discover()`, which may load classes via
reflection depending on the engine.  This is the fundamental trade-off.

#### Current mitigations

- The `URLClassLoader` is closed via try-with-resources after discovery completes
- The context classloader is set and restored on the current thread
- The returned `TestPlan` holds `TestIdentifier`s with class/method *names* (strings), not
  live `Class<?>` references, so the classloader can be GC'd

#### Assessed risks

- **Static initializers**: if a test class has `static { System.loadLibrary(...); }` or
  expensive initialization, it runs in the daemon.  Uncommon in practice — JUnit Platform's
  `selectClasspathRoots` uses bytecode scanning and typically doesn't trigger deep class loading
- **Metaspace growth**: each discovery pass loads classes via a new classloader.  Since the
  classloader is closed and unreferenced, classes are eligible for GC.  Repeated `gradle test`
  invocations on a long-lived daemon may accumulate metaspace until GC reclaims it
- **Parent classloader leakage**: the `URLClassLoader` parents to the daemon thread's context
  classloader, so test classes can see Gradle internal classes.  Classloading conflicts are
  possible but unlikely

#### Options for stronger isolation (not currently implemented)

1. **Restricted parent classloader**: a filtering classloader that delegates to the daemon
   only for JUnit Platform classes and blocks Gradle internals.  Complex but achievable
2. **Null parent with explicit JUnit jars**: `new URLClassLoader(urls, null)` with JUnit
   Platform jars in the URL array.  Tested and failed — `LauncherFactory` loads `TestEngine`
   via `ServiceLoader` from the URLClassLoader, but expects the `TestEngine` interface from
   the daemon's classloader.  The two class hierarchies are incompatible
   (`JupiterTestEngine not a subtype`).  Would require ensuring JUnit Platform classes are
   loaded from exactly one classloader
3. **Subprocess isolation**: run discovery in a short-lived subprocess (lighter than a full
   worker fork).  Complete isolation at the cost of ~1-2s startup overhead per discovery pass
4. **Eager cleanup**: null out classloader references after extracting identifiers and hint GC.
   Marginal benefit over the current try-with-resources approach

## Future work

- **Discovery Feedback**: Provide test names, count and structure to IDEs prior to running, for tree-view display
  - Console output for discovery, e.g., "Discovered 42 tests in 5 classes and 3 resource files"
  - Console output during execution should look like with Test Distribution enabled with total classes/containers count and progress.
  - **Tooling API extension**: stream the discovered test plan to IDEs over the Tooling API before execution starts, enabling tree-view population without waiting for the first worker event.
- **Test ETA**: Use actual execution times from tests to calculate total ETA for the test suite, revising estimates as tests complete.  Display ETA in console output and IDEs.
- **Test distribution strategies**: explore more sophisticated strategies that use test metadata (e.g., historical execution times, tags, or custom annotations) to optimize parallel execution while preserving cohesion where needed.
  - Start the most methods first, or balance total number of methods per worker.
  - Work-stealing by workers: idle workers can pull tests from a shared queue of remaining tests, with locking to preserve cohesion guarantees.
  - **Adaptive worker count**: derive the actual number of workers to fork from the discovered test count and historical timings, rather than always honoring `maxParallelForks` — avoids forking 12 workers for 3 tests.
  - **Fan-out JUnit Platform Suite members**: today `@Suite` + `@SelectClasses` produces a single top-level container, so all suite members run in one worker.  A future strategy could detect suite containers and redistribute their class children across workers when the suite's contract permits it (no `@BeforeSuite`/`@AfterSuite` dependencies).
- **Discovery result caching between invocations**: cache the daemon's `TestPlan` keyed on test source + classpath inputs so that repeated `gradle test` runs with no input changes skip the `URLClassLoader` discovery pass entirely.  Requires a cache invalidation policy that matches Gradle's task input model.
- **Junit Selectors are arguments to task filtering**: Support `selectUniqueId` and other JUnit selectors as first-class arguments to `Test` tasks somehow, allowing users to specify discovery selectors directly in their build scripts or via command-line options.  This would enable more flexible and powerful test selection based on JUnit Platform's discovery model.  See https://github.com/gradle/gradle/pull/29535
- **Discovery Isolation Improvements**: Use a worker for discovery with a custom classloader setup to achieve stronger isolation of test classes from the daemon, mitigating risks of static initializers and classloader leaks while still enabling daemon-side discovery.  Perhaps have this discovery worker communicate to the daemon and other execution workers simultaneously.
- **Previously run failures**: Rerun failing methods first within classes, not any class with a failure.
- **Instant execution**: Remove "collect-then-execute" and work towards "immediately execute" tests sent to workers.
- **Send test batches**: Don't send definition-by-definition to the worker, but batch them in groups (e.g., 10 at a time) to amortize serialization overhead while still enabling dynamic test scheduling and early execution.
- **Test Retry as a first-class feature**: See https://github.com/gradle/gradle/pull/29535
- **DV Changes**: TD and PTS do their own test discovery - can we change them to rely on Daemon-side discovery?
