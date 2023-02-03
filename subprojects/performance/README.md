
This project contains the Gradle performance test suite. For information about the test infrastructure, see [internal-performance-testing](../internal-performance-testing)

## Performance test scenarios

A performance test scenario is made up of 2 parts:

1. A test build to use. The test builds are generated from templates and are defined in the [`build-logic/performance/src/main/groovy/performance-templates.gradle`](build-logic/performance/src/main/groovy/performance-templates.gradle) plugin. 
2. Details for how to invoke Gradle (or Maven). This includes the tasks to run, the JVM args, whether the daemon or tooling API should be used to invoke Gradle, and so on.

A performance test configure various fixtures to describe each scenario. The fixtures will then run the scenario several times to warm up, and then several more times, capturing metrics.
The metrics are collected in a database under `~/.gradle-performance-test-data` and a report is generated into `build/performance-tests/report`.

### Performance test builds

The build templates live in [`src/templates`](src/templates). Each template build is parameterized to some degree. For example, it is possible for define how many projects, source 
or test files to generate for a performance test build.

There is a task defined in [`build-logic/performance/src/main/groovy/performance-templates.gradle`](build-logic/performance/src/main/groovy/performance-templates.gradle) for each performance test build, that specifies which templates to use and the build parameters.

### Metrics collected

- Total build execution time.
- Build configuration time.
- Task execution time.
- Heap consumption at the end of the build.
- Total heap usage during the build

### Report

There is a `performance:report` task that generates a static HTML report from the contents of the database in `~/.gradle-performance-test-data`. This report allows the results over
time to be visualized.

The report for the most recent test suite run against master is [here](https://builds.gradle.org/repository/download/Gradle_Check_PerformanceTestCoordinator/.lastFinished/report-performance-performance-tests.zip%21/report/index.html?branch=master)

### Tracking down performance regressions

For tracking down performance regressions see [Tracking down performance regressions with `git bisect`](docs/performance-bisect.md).
