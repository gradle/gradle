## Performance test infrastructure

A performance test is implemented as a Spock test. There are several base classes and a performance test should extend one of these:

- [`AbstractCrossVersionPerformanceTest`](src/main/groovy/org/gradle/performance/AbstractCrossVersionPerformanceTest.groovy): A performance test that runs a particular scenario using the 
Gradle version under test and using several other baseline Gradle versions. The test compares the results to report on performance regressions relative to these baseline versions. 
- `AbstractCrossBuildPerformanceTest`: A performance test that runs several different scenarios using the Gradle version under test and compares the results.
- `AbstractGradleVsMavenPerformanceTest`: A performance test that runs a particular scenario using the Gradle version under test and a particular Maven version and compares the results.

The Gradle version under test is often a Gradle distribution built from the same source git revision as the performance test suite, but may be any Gradle distribution. 

The baseline Gradle versions are often one or more released versions of Gradle, but may be any Gradle distribution.
