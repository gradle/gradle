# Incremental Build Improvements

This spec defines some improvements to improve incremental build and task up-to-date checks

# Establishing Baseline

## ~~Story: Add 'profiler' (YourKit) hook to performance test harness~~

- Make it easier for us to collect profiling data and share profiling setup
- ~~Gradle dev adds `YJP_HOME` (Yourkit home directory path) or `YJP_AGENT_PATH` (Yourkit agent library file path) environment variable.~~
- ~~Gradle dev writes a performance test that extends AbstractCrossBuildPerformanceTest or AbstractCrossVersionPerformanceTest.~~
- ~~Gradle dev enables YJP by passing `-Porg.gradle.performance.use_yourkit` project property in running the performance test.~~
  - example use: `./gradlew performance:performanceTest -Porg.gradle.performance.use_yourkit -D:performance:performanceTest.single=NativePreCompiledHeaderPerformanceTest`
- ~~Yourkit agent options are loaded from `~/.gradle/yourkit.properties` by default.~~
  - Yourkit supports these startup options: https://www.yourkit.com/docs/java/help/startup_options.jsp .
- ~~Yourkit profiling snapshot data get saved to `~/Snapshots` by default. The file name contains the test project name and display name from the performance test.~~

### Test coverage

- TBD, whatever makes sense (this should touch test infrastructure only)

## ~~Story: Compare tasks using IncrementalTaskInputs with "regular" tasks~~

- Vary number of inputs
- If difference between calculating up-to-date checks is identical or within an order of magnitude, remove this as a variable in future plans.
- Collect numbers for a report

### Test coverage

- This does not need to be automated
- Compare time to perform up-to-date checks for otherwise identical tasks at
   - 1, 10, 100, 1000 and 10000 inputs

### Result

No meaningful difference (see detailed numbers in Google sheet).

## ~~Story: Compare "one change" and "no change" cases~~

- Vary number of inputs
- Compare no input change case with one input change case
- If difference between calculating up-to-date checks is identical or within an order of magnitude, remove this as a variable for now and focus on 'no change' cases.
- Future stories may address the "ripple" effect of a single input invalidating multiple tasks in a chain.
- Collect numbers for a report

### Test coverage

- This does not need to be automated
- Collect time to perform up-to-date checks at
   - 1, 10, 100, 1000 and 10000 inputs

### Result

No meaningful difference (see detailed numbers in Google sheet).

## ~~Story: Find breaking point for input and output sizes~~

### Scenario 1: vary inputs

- Vary number of inputs
- Vary sizes of inputs
- Breaking point is the point where Gradle fails due to memory (reported issue) or takes a "long" time (>60 seconds for a single task).

#### Test coverage

- This does not need to be automated
- Collect time to perform up-to-date checks at
   - 1, 10, 100, 1000 and 10000 inputs
   - 10k, 50k, 100k, 500k, 1m inputs (smaller if impractical)
- Collect time to perform up-to-date checks for 100 inputs at
   - 1MB, 10MB, 50MB, 100MB inputs (smaller if impractical)

### Scenario 2: vary outputs

- Vary number of outputs
- Vary sizes of outputs
- Breaking point is the point where Gradle fails due to memory (reported issue) or takes a "long" time (>60 seconds for a single task).

#### Test coverage

- This does not need to be automated
- Collect time to perform up-to-date checks at
   - 1, 10, 100, 1000 and 10000 outputs
   - 10k, 50k, 100k, 500k, 1m outputs (smaller if impractical)
- Collect time to perform up-to-date checks for 100 outputs at
   - 1MB, 10MB, 50MB, 100MB outputs (smaller if impractical)

### Result

- It depends on the heap size. With 10000 tasks of 10000 input files, it runs out of memory with a 4GB max heap size.
- With 1GB heap, 1000 tasks with 1000 input files is successful (total of 1 million inputs), but 1000 tasks with 2000 input files fails.
Because of task execution history, the file hashes from the previous build are kept in memory. There was about 2.5 million FileHashSnapshot
instances in memory when the OOM occured.

## Story: Update performance generator to create representative Java project

Uses java-lang/jvm-component software model plugins.

A representative Java project:
- 1 set of main sources
- 1 set of unit test sources
- Project dependencies
- External dependencies
  - use generated maven repository with generated jar files with realistic sizes (200k-2000k)
  - define dependencies in build by using old-model configurations
  - wire old-model configurations in to new-model generated tasks (compile, test)
  - add testCompile configuration that extends compile configuration
    - testCompile configuration add junit dependency
  - add testRuntime configuration that extends testCompile
    - use testRuntime for test execution task
- Emulated checkstyle task per source set (main, test)
- Unit test task

2 different sizes of generated projects: small and large.

All builds:
- multi-project builds
- max 10 classes per package
- 50 source lines per class

Small build:
- 10 projects
- 100 classes per project
- 5 external dependencies per project, 20 unique external dependencies
- up to 3 project dependencies per project

Large build:
- 100 projects
- 1000 classes per project
- 50 external dependencies per project, 200 unique external dependencies
- up to 10 project dependencies per project


### Test coverage

- Performance test that runs against latest release and master branch.

### Open Issues

~~- Need to incorporate test execution into this as well~~

~~- How are we going to handle external dependencies while the jvm-component plugins do not support external dependencies?~~
- do we need to simulate integration tests?

## Story: Update performance generator to create representative C/C++ project

See discussion about parameters.  Uses cpp software model plugins.

### Test coverage

- Performance test that runs against 2.6 and latest release

## ~~Story: Profile Java representative build with parallel execution mode~~

- Record profile findings

### Test coverage

- Test with --parallel --max-workers=4
- add test scenario to NewJavaPluginPerformanceTest for parallel execution to existing test

## Story: Speed up File metadata lookup in task input/output snapshotting

File metadata operations .isFile(), .isDirectory(), .length() and .lastModified are hotspots in task input/output snapshotting.

The Java nio2 directory walking method java.nio.file.Files.walkFileTree can pass the file metadata used for directory scanning to "visiting" the file tree so that metadata (BasicFileAttributes) doesn't have to be re-read.

### Implementation

- For JDK7+ with UTF-8 file encoding, use a nio2 file walker implemention.
    - Cache isDirectory()/getSize()/getLastModified() in FileVisitDetails from BasicFileAttributes gathered from walking
- Otherwise, use default file walker implementation (current behavior).
    - Use a caching FileVisitDetails for getSize()/getLastModified() to cache on first use.
    - Maybe reuse isFile/isDirectory result from the walker implementation
- Replace calls to getFiles() in DefaultFileCollectionSnapshotter with a visitor

### Test coverage

- Test that correct implementation is chosen for JDK platform and file encoding
- Test that a file walker sees a snapshot of tree even if the tree is modified after walking has started.
- Generate file tree and walk with JDK7+ file walker and non-nio2 file walker. Attributes and files should be the same for both.
- Test that the visited files is the same as inputs.getAsFileTrees().getFiles() when snapshotting.
- Performance gains will be measured from existing performance tests.
- Expect existing test coverage will cover behavior of input/output snapshotting and file collection operations.

## Story: Add caching to Specs returned from PatternSet.getAsSpecs()

Evaluating patterns is a hotspot in directory scanning. The default excludes patterns contains 28 entries. Checking all rules for each file sums up in a lot of operations. Adding caching will improve performance of subsequent incremental builds.

### Test coverage

TBD

## Story: Inline the data from the file hash cache into the task history cache

Change `DefaultFileCollectionSnapshotter` to store (length, last-modified, hash)
in the `FileCollectionSnapshot` implementation it creates. This is referenced by
the task history cache.

When deciding whether something has changed, iterate over the elements of the
`FileCollectionSnapshot` and use the (length, last-modified, hash) from the
snapshot. Do not fetch this information from the file hash cache. If something
has changed, hash it, and add the (length, last-modified, hash) tuple to the
file hash cache. Also make a copy of the snapshot and update it with this new
entry. This copy will be used as the new snapshot in the task history.

What this means is that file hash cache is used only for new and modified files,
which means there’s no point caching these entries in-memory (except perhaps for
the duration of a build). For unmodified files, there’s never a cache miss. When
nothing has changed for a snapshot, we also don’t need to hold a lock on the
cache, which means less work and better concurrency when most snapshots are
unmodified.

### Test coverage

TBD


## Story: Reduce the in-memory size of the task history cache

1. Discard history that will never be used, eg for tasks that no longer exist,
or that is unlikely to be used, eg for tasks in builds other than the current.
2. Keep a hash of the file snapshots in memory rather than the file snapshots
themselves. It’s only when we need to know exactly which files have changed that
we would need to load up the snapshot.
3. Do something similar for the task properties as well.
4. Reduce the cost of a cache miss, by improving the efficiency of serialisation
to the file system, and reducing the cache size.
5. Reduce the cost of a cache miss, by spooling to an efficient transient second
level cache, and reducing the cache size.

### Test coverage

TBD

## Story: Use source file dependency information in up-to-date checks

TBD

### Test coverage

- TBD

## Story: TBD

TBD

### Test coverage

- TBD

## Open Issues

- Check behavior of Prebuilt Libraries with large number of headers (https://discuss.gradle.org/t/native-performance-issue-with-no-op-builds-with-boost-as-a-dependency)
- See incremental-build-old.md for other ideas and specific issues.
- Profiler support: Skip warm-up or have explicit opt-in for warm up profiling?
  - Conditionally enabling the profiler in the code by using Yourkit API.
- Profiler support: For cross-version tests, skip all versions except the current?
- Profiler support: Do we care about conditionally profiling the CLI and daemon processes (such that the profiling options should be configurable for each)?
