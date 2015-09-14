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

## Story: Compare tasks using IncrementalTaskInputs with "regular" tasks

- Vary number of inputs
- If difference between calculating up-to-date checks is identical or within an order of magnitude, remove this as a variable in future plans.
- Collect numbers for a report

### Test coverage

- This does not need to be automated
- Compare time to perform up-to-date checks for otherwise identical tasks at
   - 1, 10, 100, 1000 and 10000 inputs

## Story: Compare "one change" and "no change" cases

- Vary number of inputs
- Compare no input change case with one input change case
- If difference between calculating up-to-date checks is identical or within an order of magnitude, remove this as a variable for now and focus on 'no change' cases.
- Future stories may address the "ripple" effect of a single input invalidating multiple tasks in a chain.
- Collect numbers for a report

### Test coverage

- This does not need to be automated
- Collect time to perform up-to-date checks at
   - 1, 10, 100, 1000 and 10000 inputs

## Story: Find breaking point for input and output sizes

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
