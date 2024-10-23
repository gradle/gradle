<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [Tracking down performance regressions with `git bisect`](#tracking-down-performance-regressions-with-git-bisect)
  - [Identify the test that caused the regression](#identify-the-test-that-caused-the-regression)
  - [Modify test for regression search](#modify-test-for-regression-search)
  - [Perform the search](#perform-the-search)
  - [Verify the results](#verify-the-results)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Tracking down performance regressions with `git bisect`

When you see a performance regression, either by looking at the performance graph
or by having a new failing test, given the current slow feedback cycle, it can
be difficult to track the exact commit that triggered the regression.

The most convenient way to find the commit is to use `git bisect`. Before doing so
you need to
 1. identify the test that exposes the regression
 2. set up the test to search only for the regression you are looking after to speed up the search for the regression.

## Identify the test that caused the regression

There is no mapping between the result (i.e. the graph) and the test class, yet.
Therefore, you need to search for it in the code base. For example, the test
`native build medium header file change` is included in the class `RealWorldNativePluginPerformanceTest`.

Moreover, we also need to know the sample used for the test. For this example, it is `mediumNativeMonolithic`.

## Modify test for regression search

First, you should change the test so that
 - only the version you are interested in is used as a reference
 - only the test you are interested in is executed
 - tighten the regression limits to get significant results
 - only search for memory/execution time regressions depending on what you are interested in
 
For our example, let's assume we want to track down a performance regression for `native build medium header file change`. 

We modify `RealWorldNativePluginPerformanceTest` to only include the test we are interested in:
```java
    @Ignore
    @Unroll("Project '#testProject' measuring incremental build speed")
    def "build real world native project"() {
...
}

    @Unroll('Project #buildSize native build #changeType')
    def "build with changes"(String buildSize, String changeType, Amount<Duration> maxExecutionTimeRegression, String changedFile, Closure changeClosure) {
...
        runner.maxExecutionTimeRegression = maxExecutionTimeRegression
        runner.targetVersions = ['2.14']
        runner.useDaemon = true
...
        where:
        // source file change causes a single project, single source set, single file to be recompiled.
        // header file change causes a single project, two source sets, some files to be recompiled.
        // recompile all sources causes all projects, all source sets, all files to be recompiled.
        buildSize | changeType              | maxExecutionTimeRegression | changedFile                       | changeClosure
//        "medium"  | 'source file change'    | millis(300)                | 'modules/project5/src/src100_c.c' | this.&changeCSource
        "medium"  | 'header file change'    | millis(140)                | 'modules/project1/src/src50_h.h'  | this.&changeHeader
//        "medium"  | 'recompile all sources' | millis(1500)               | 'common.gradle'                   | this.&changeArgs
    }
...
```

In order to include only performance regressions we modify `CrossVersionPerformanceResults`:

```java
...
    void assertCurrentVersionHasNotRegressed() {
        def slower = checkBaselineVersion({ it.fasterThan(current) }, { it.getSpeedStatsAgainst(displayName, current) })
//        def larger = checkBaselineVersion({ it.usesLessMemoryThan(current) }, { it.getMemoryStatsAgainst(displayName, current) })
//        if (slower && larger) {
//            throw new AssertionError("$slower\n$larger")
//        }
        if (slower) {
            throw new AssertionError(slower)
        }
//        if (larger) {
//            throw new AssertionError(larger)
//        }
    }
...
```

In order to only test against `2.14` and not the latest release we modify `CrossVersionPerformanceTestRunner`:

```java
...
    static LinkedHashSet<String> toBaselineVersions(ReleasedVersionDistributions releases, List<String> targetVersions, boolean adhocRun) {
...
//            if (!targetVersions.contains('nightly')) {
//                // Include the most recent final release if we're not testing against a nightly
//                baselineVersions.add(mostRecentRelease)
//            } else {
//                baselineVersions.add(mostRecentSnapshot)
//            }
        }
        baselineVersions
    }
...
```

## Perform the search

While doing the search no other activity should be done on the machine. Therefore, it
is best to have a dedicated machine running the search, e.g., using the CI infrastructure.

In order to check if the test passes for the current revision you can use [check-rev.sh](check-rev.sh).

All files from ~/.gradle-bisect-override will be copied to the working directory. Make changes to files under that directory since the script will reset any changes.
This means that all the files we modified before should be copied into this directory.

usage:

```bash
mkdir ~/.gradle-bisect-override
# copy test class to override directory and make changes in that directory
rsync -aRv subprojects/performance/src/integTest/groovy/org/gradle/performance/RealWorldNativePluginPerformanceTest.groovy \
           subprojects/performance/src/testFixtures/groovy/org/gradle/performance/fixture/{CrossVersionPerformanceResults,CrossVersionPerformanceTestRunner}.groovy \
           ~/.gradle-bisect-override

# check revision
./check_rev.sh RealWorldNativePluginPerformanceTest mediumNativeMonolithic
```

Now you can use the script automatically with `git bisect`.

```bash
git bisect start HEAD REL_2.14 --  # HEAD=bad REL_2.14=good
git bisect run check_rev.sh RealWorldNativePluginPerformanceTest mediumNativeMonolithic
```

This will take some time, depending on the number of commits. After each step, the test results will
be copied to `~/.gradle-bisect-results`. In order to have an overview of the current state
of the bisect, you can easily use grep on that directory.

```bash
mymachine:~$ grep -A 1 "^Speed" ~/.gradle-bisect-results/*.xml

/home/vmadmin/.gradle-bisect-results/result_0_cd420bfd_2016-06-17-13:15:11.xml:Speed Results for test project 'mediumNativeMonolithic' with tasks build: we're slower than 2.14.
/home/vmadmin/.gradle-bisect-results/result_0_cd420bfd_2016-06-17-13:15:11.xml-Difference: 3.8 ms slower (3.8 ms), 0.39%, max regression: 140 ms
--
/home/vmadmin/.gradle-bisect-results/result_1_00f795e2_2016-06-17-13:10:45.xml:Speed Results for test project 'mediumNativeMonolithic' with tasks build: we're slower than 2.14.
/home/vmadmin/.gradle-bisect-results/result_1_00f795e2_2016-06-17-13:10:45.xml-Difference: 170.4 ms slower (170.4 ms), 17.21%, max regression: 140 ms
--
/home/vmadmin/.gradle-bisect-results/result_1_29731dc5_2016-06-17-13:06:17.xml:Speed Results for test project 'mediumNativeMonolithic' with tasks build: we're slower than 2.14.
/home/vmadmin/.gradle-bisect-results/result_1_29731dc5_2016-06-17-13:06:17.xml-Difference: 155.4 ms slower (155.4 ms), 15.62%, max regression: 140 ms
```

## Verify the results

In order to verify that the commit really introduced the performance regression, it
is advisable to revert the commit on `HEAD` and see if that
fixes the regression.

Moreover, one should check if the test failures have not been caused by something other than the
performance test by looking at the test results in `~/.gradle-bisect-results`.
