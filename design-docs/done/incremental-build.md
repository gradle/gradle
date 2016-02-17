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

## Story: Performance test for native incremental build where some files require recompilation

##### Constraints
- Change happens after a previous build, so it is not a clean build
- Needs to be something that causes the linker to run, so not just a comment change

##### Implementation
- modify existing internal Performance testing framework to support measurements for these scenarios
  - callbacks for before and after invocation with the information about the current test invocation
    - test phase: warmup or measurement
    - test loop number
    - maximum number of loops
    - BuildExperimentSpec instance
  - in the before invocation callback, we can make changes to files
  - add ability to omit measurements in the after invocation callback
    - the build invocation that is done before changing files has to be omitted from measurements
- implementation plan for the test:
  - The build is run multiple times. Use the features added in the previous step for implementing the behaviour.
    - on odd build loops, run the build and omit the measurement
    - on even build loops, do the modification and run the build and record the measurement
  - run the build loop 2 times in warmup phase and 10 times in execution phase (modification is made on every second loop).
  - Create 2 new builds for performance tests that are downsized from the `nativeMonolithic` build
    - `smallNativeMonolithic`: 1% of `nativeMonolithic` size
        - use for all 3 scenarios
    - `mediumNativeMonolithic`: 10% of `nativeMonolithic` size
        - use for 2 scenarios (1 file changes, few files change)

example of using `BuildExperimentListener` for testing
```
    @Unroll('Project #type native build 1 change')
    def "build with 1 change"() {
        given:
        runner.testId = "native build ${type} 1 change"
        runner.testProject = "${type}NativeMonolithic"
        runner.tasksToRun = ["assemble"]
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['2.8', 'last']
        runner.buildExperimentListener = new BuildExperimentListener() {
            @Override
            GradleInvocationCustomizer createInvocationCustomizer(BuildExperimentInvocationInfo invocationInfo) {
                null
            }

            @Override
            void beforeInvocation(BuildExperimentInvocationInfo invocationInfo) {
                if(invocationInfo.loopNumber % 2 == 0) {
                    // do change

                } else if (invocationInfo.loopNumber > 2) {
                    // remove change

                }
            }

            @Override
            void afterInvocation(BuildExperimentInvocationInfo invocationInfo, MeasuredOperation operation, BuildExperimentListener.MeasurementCallback measurementCallback) {
                if(invocationInfo.loopNumber % 2 == 1) {
                    measurementCallback.omitMeasurement()
                }
            }
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        type     | maxExecutionTimeRegression
        "small"  | millis(1000)
        "medium" | millis(5000)
    }
```


#### Scenarios:

- Incremental build where 1 file requires recompilation
    - 1 C source file changed
- Incremental build where a few files require recompilation
    - 1 header file (included in a few source files) changed
- Incremental build where all files require recompilation
    - 1 compiler option changed


### Test coverage

- Test with --parallel --max-workers=4
- add test scenario to NewJavaPluginPerformanceTest for parallel execution to existing test

# General Performance Improvements

## Story: Changes to reduce byte/char array allocations and array copying

- Just do it

## Story: Changes for adjusting collection sizes when final size is known

- Just do it

## Story: High number of UnknownDomainObjectExceptions when resolving libraries in native projects.

### Implementation

- remove the use of UnknownDomainObjectException as flow control of normal program flow in LibraryBinaryLocator implementations (ChainedLibraryBinaryLocator, PrebuiltLibraryBinaryLocator, ProjectLibraryBinaryLocator). 
  - return null from LibraryBinaryLocator.getBinaries method when binaries cannot be located.
  - since exceptions won't be used for passing detailed error messages, they will be removed.
- LibraryResolveException should be thrown in DefaultLibraryResolver.resolveLibraryBinary method if LibraryBinaryLocator returns a null.

### Test coverage

- No new test coverage is needed. Change existing test to follow the changed interface contract of LibraryBinaryLocator.

## ~~Story: Speed up File metadata lookup in task input/output snapshotting~~

File metadata operations .isFile(), .isDirectory(), .length() and .lastModified are
hotspots in task input/output snapshotting.

The Java nio2 directory walking method java.nio.file.Files.walkFileTree can pass the file
metadata used for directory scanning to "visiting" the file tree so that metadata
(BasicFileAttributes) doesn't have to be re-read.

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
- Performance gains will be measured from existing performance tests.
- Expect existing test coverage will cover behavior of input/output snapshotting and file collection operations.

## ~~Story: Reduce the in-memory size of the task history cache by interning file paths~~

### Implementation

- Use Guava's [`Interners.newWeakInterner()`](http://google.github.io/guava/releases/18.0/api/docs/com/google/common/collect/Interners.html#newWeakInterner%28%29) to create a cache `StringInterner` for sharing the file path Strings. Place this cache in `GlobalScopeServices` so that the instance lives across multiple builds in the daemon.
- use the `StringInterner` to intern all duplicate path names contained in `fileSnapshots`, `taskArtifacts`, `outputFileStates` and `fileHashes` caches.
- Implementation can be based on the solution developed in the spike. The commit is https://github.com/gradle/gradle/commit/d26d4ce1098e0eee9896279cbeabefb1ca3e871c .

### Test coverage

- Add basic unit test coverage for StringInterner
  - interning different string instances with similar content return the first instance that was interned
  - allows calling method with null, returns null in that case

## ~~Story: Add caching to Specs returned from PatternSet.getAsSpecs()~~

Evaluating patterns is a hotspot in directory scanning. The default excludes patterns
contains 28 entries. Checking all rules for each file sums up in a lot of operations.
Adding caching will improve performance of subsequent incremental builds.

### Implementation

Assumption: PatternSet class is part of the Gradle Public API and we cannot change it's interface.

#### ~~1. phase - target release Gradle 2.9~~

Spike commit: https://github.com/lhotari/gradle/commit/f235117fd0b8b125a8220c45dca8ee9dc2331559

- Mainly based on the spike commit
- Move Spec<FileTreeElement> creation logic to separate factory class from PatternSet class (currently in getAsSpec, getAsIncludeSpec, getAsExcludeSpec methods)
- Add caching for Spec<FileTreeElement> instance creation and evaluation results
- Only add caching to Spec<FileTreeElement> instances that are created from the include and exclude patterns.
- A PatternSet can contain a list of includeSpecs and excludeSpecs. Don't add caching to these.

#### Test coverage for 1. phase

- Test that Spec<FileTreeElement> includes (added with PatternSet.include(Spec<FileTreeElement> spec)) and excludes (added with PatternSet.exclude(Spec<FileTreeElement> spec)) are not cached.
- Existing PatternSet tests cover rest of the changes since there are no planned behavioural or API changes for 1. phase.

#### ~~2. phase - target release Gradle 2.11~~

Goal: manage the cache instance in Gradle infrastructure instead of a singleton instance
- Use default non-caching PatternSpecFactory in PatternSet class, replace use of CachingPatternSpecFactory with plain PatternSpecFactory
- Make the Gradle infrastructure manage the CachingPatternSpecFactory instance.
- Create a new PatternSet subclass that takes the PatternSpecFactory instance in the constructor.
- Replace usage of PatternSet class with the new subclass in Gradle core code. Wire the CachingPatternSpecFactory instance to the instances of the new PatternSet subclass.

## ~~Story: Use source #include information as discovered inputs~~

This story adds an internal mechanism way for native complication tasks task to register additional inputs that are discovered when parsing the source file inputs.  At the end of execution, the discovered inputs are recorded in the task's execution history, and will be compared when performing up-to-date checks for the next task execution.

During native compilation, the source file parser searches the include path for any `#include` directives. Each header file found will be registered as a discovered input. In addition, each file location that is inspected as a candidate header file will be registered as a discovered input: the fact that a file is _not_ present will also be recorded as an input.

A subsequent story will be required to turn "discovered inputs" into a generally available public feature.

### Test coverage

- Reuse existing test coverage for incremental native compilation
- Measure improvement/regression with native perf tests

