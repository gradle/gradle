
This project contains the Gradle performance test infrastructure and test suite.

## Test infrastructure

A performance test is implemented as a Spock test. There are several base classes and a performance test should extend one of these:

- [`AbstractCrossVersionPerformanceTest`](src/testFixtures/groovy/org/gradle/performance/AbstractCrossVersionPerformanceTest.groovy): A performance test that runs a particular scenario using the 
Gradle version under test and using several other baseline Gradle versions. The test compares the results to report on performance regressions relative to these baseline versions. 
- `AbstractCrossBuildPerformanceTest`: A performance test that runs several different scenarios using the Gradle version under test and compares the results.
- `AbstractGradleVsMavenPerformanceTest`: A performance test that runs a particular scenario using the Gradle version under test and a particular Maven version and compares the results.
- `BuildReceiptPluginPerformanceTest`: A variation on `AbstractCrossBuildPerformanceTest`.

The Gradle version under test is often a Gradle distribution built from the same source git revision as the performance test suite, but may be any Gradle distribution. 

The baseline Gradle versions are often one or more released versions of Gradle, but may be any Gradle distribution.

## Performance test scenarios

A performance test scenario is made up of 2 parts:

1. A test build to use. The test builds are generated from templates and are defined in the [`performance.gradle`](performance.gradle) build script. 
2. Details for how to invoke Gradle (or Maven). This includes the tasks to run, the JVM args, whether the daemon or tooling API should be used to invoke Gradle, and so on.

A performance test configure various fixtures to describe each scenario. The fixtures will then run the scenario several times to warm up, and then several more times, capturing metrics.
The metrics are collected in a database under `~/.gradle-performance-test-data` and a report is generated into `build/performance-tests/report`.

### Performance test builds

The build templates live in [`src/templates`](src/templates). Each template build is parameterized to some degree. For example, it is possible for define how many projects, source 
or test files to generate for a performance test build.

There is a task defined in [`performance.gradle`](performance.gradle) for each performance test build, that specifies which templates to use and the build parameters.

### Metrics collected

- Total build execution time.
- Build configuration time.
- Task execution time.
- Heap consumption at the end of the build.
- Total heap usage during the build

### Report

There is a `performance:report` task that generates a static HTML report from the contents of the database in `~/.gradle-performance-test-data`. This report allows the results over
time to be visualized.

The report for the most recent test suite run against master is [here](https://builds.gradle.org/repository/download/Gradle_Master_Performance_Linux/.lastFinished/results/report/index.html)
