
This project contains the Gradle performance test suite. For information about the test infrastructure, see [internal-performance-testing](../internal-performance-testing/README.md)

## Performance test scenarios

A performance test scenario is made up of 2 parts:

1. A test build to use. The test builds are generated from templates and are defined in the [performance-templates](../../build-logic/performance-testing/src/main/groovy/gradlebuild.performance-templates.gradle) plugin.
2. Details for how to invoke Gradle (or Maven). This includes the tasks to run, the JVM args, whether the daemon or tooling API should be used to invoke Gradle, etc.

A performance test configures various fixtures to describe each scenario. The fixtures will then run the scenario several times to warm up and then several more times, capturing metrics.
The metrics are collected in a database under `~/.gradle-performance-test-data` and a report is generated into `build/performance-tests/report`.

### Performance test builds

The build templates live in [`src/templates`](src/templates). Each template build is parameterized to some degree. For example, it is possible to define how many projects, source
or test files to generate for a performance test build.

There is a task defined in [performance-templates](../../build-logic/performance-testing/src/main/groovy/gradlebuild.performance-templates.gradle) for each performance test build, that specifies which templates to use and the build parameters.

### Metrics collected

- Total build execution time.
- Build configuration time.
- Task execution time.
- Heap consumption at the end of the build.
- Total heap usage during the build

### Report

A `performance:report` task generates a static HTML report from the contents of the database in `~/.gradle-performance-test-data`. This report allows the results over
time to be visualized.

The reports for the most recent test suite run against master can be found at:

- [Linux Performance Test](https://builds.gradle.org/repository/download/Gradle_Master_Check_PerformanceTestTestLinux_Trigger/.lastFinished/performance-test-results.zip!/report/index.html)
- [macOS Performance Test](https://builds.gradle.org/repository/download/Gradle_Master_Check_PerformanceTest7_Trigger/.lastFinished/performance-test-results.zip!/report/index.html)
- [Windows Performance Test](https://builds.gradle.org/repository/download/Gradle_Master_Check_PerformanceTest6_Trigger/.lastFinished/performance-test-results.zip!/report/index.html)

Running all the tests at once will be resource-intensive and long (multiple hours).
However, you can find instructions for running a single performance test at the top of each Graph page.
The result of that local run will not include comparisons with previous executions. Still, you can then look at the numbers and verify that your changes do not introduce a regression.

### Tracking down performance regressions

For tracking down performance regressions, see [Tracking down performance regressions with `git bisect`](docs/performance-bisect.md).
