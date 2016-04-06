Investigate and improve test execution startup time.

## Implementation plan

- Review and update performance tests to measure this and lock in improvements.
- Measure Gradle vs Maven and proceed further based on this.
    - Add performance tests to lock this in.
- Profile test builds and use results to select improvements to implement
- Change progress reporting to indicate when Gradle _starts_ running tests. Currently, progress is updated only on completion of the first test.
  This doesn't make test execution faster, makes for a fairer subjective comparison between Gradle and Maven.

### Potential improvements

- Fix hotspots identified by profiling
- Investigate file scanning done by the `Test` task. Initial observations have this task scanning its inputs many times. Investigate and fix.
- Don't attach source directories to `Test` task, when not required.
- Fix `Test` task inputs so that candidate class files hash is not required.
- Fix file snapshot calculation to handle directories that are declared as multiple inputs or outputs for a task.

## Stories

### Performance tests establish test startup baseline

Ensure there is a performance test build that:

- Uses the current Java plugin
- Has many projects
- Has reasonable number of test classes per project
- Has JUnit tests

Use this build in a performance test that:

- Runs `cleanTest test`
- Uses the daemon

Tune the number of projects in the test build based on this. We're aiming for a build that takes around 30 - 60 seconds.

Note: review the existing test builds and _reuse_ an existing build and existing templates if possible. Should also reuse existing test execution performance test class if possible.

### Compare Maven and Gradle build performance

Fix the POM generation for the above test build, if it is broken.

Compare the execution time of a `clean build` and up-to-date `build` for Maven and Gradle.

Introduce a cross-build test case that allows comparison between Maven and Gradle to be automated. Use a separate results store for that, and minimize the amount of changes required to the reporting tool (it's not necessary to be able to compare results appropriately)

Allow comparison between various Maven versions. By default, will use the latest release. Test case should provision Maven and cache it to a local directory. Invocations of Gradle should not use the standard `.m2` repository but a temporary one, as not to break the dependency resolution integration tests.

### Investigate file scanning done by the `Test` task

The `Test` task has been observed scanning its inputs more times that expected. Investigate whether this is the case, and why it is happening.

Note: this goal for this story is only to understand the behaviour, not to fix anything.

#### Some results

The following directories are scanned in the up-to-date check for `test`:

- `build/classes/test` is scanned 4 times
- `build/classes/main` is scanned once
- `src/test/java` is scanned once

Each of these should be scanned once. In addition, the source directory should only be scanned when using very old versions of TestNG that use source annotations.

The following directory is scanned during execution of `test`:

- `build/classes/test`. This is scanned by test detection (more on this below)

The following directories are scanned at the end of the task to detect outputs:

- `build/reports/tests` is scanned twice
- `build/test-results` is scanned twice
- `build/test-results/binary/test` is scanned once

Each of these should be scanned once.

### `Test` task progress logging reports the start of test execution

Change progress reporting to indicate when Gradle _starts_ running tests. Currently, progress is updated only on completion of the first test.
This doesn't make test execution faster, makes for a fairer subjective comparison between Gradle and Maven.

Add some basic test coverage for test progress logging. Use the progress logging test fixture.

### Spike generating the HTML and XML reports in parallel

Spike generating the HTML reports at the same time as the XML reports.

The idea is to try running `Binary2JUnitXmlReportGenerator.generate()` in one thread and `TestReporter.generateReport()` in another. It is not to attempt to generate each
HTML or XML report output file in parallel (though this could be another spike).

### Generate test reports concurrently

Refactor the worker thread pool used by native compilation so that the `Test` task can reuse it to generate the HTML and XML report files in parallel, subject to
max parallel workers constraints.

Do something useful with exceptions collected during generation.

#### Implementation
This should reuse the `BuildOperationQueue` used by native compilation.  `BuildOperationQueue` should be modified so that it
better deals with failure in the thread that is generating the build operations.  Currently, this thread (the main task thread)
1. creates the queue, then 2. iterates over some stuff and adds operations to the queue, then 3. finally waits for completion.
The problem is when #2 fails with an exception after having queued some stuff up - these operations will continue to run, even
though the main task thread has finished running the task and is off doing something else.  When a failure occurs while generating
build operations, we should instead discard any queued operations and block until the currently running operations are finished,
then propagate the failure (and any build operations failures too).

Instead of `BuildOperationProcessor.newQueue(worker, …)` that returns a queue that you mess with, we might have
`BuildOperationProcessor.run(BuildOperationWorker<T> worker, Action<BuildOperationQueue<T>> generator)` that would create the
queue, run the generator to populate the queue, wait for the result and clean up on failure.

(when `T` is `Runnable` we can leave out the `worker` - we would just run the operations)

#### Some Results
The following are running `gradle cleanTest test`.  For each data point, there were a couple of warm-up runs, followed by several runs whose results were averaged together.
All times are in seconds.

The single10000/25000/50000 test sets are single project builds with 10000, 25000, and 50000 tests.

Test Report Generation time - this is a measure of the total time spent generating test reports.

Branch | mediumWithJUnit | largeWithJUnit | single10000 | single25000 | single50000
------ | --------------- | -------------- | ----------- | ----------- | -----------
Mar 21 snapshot | 3.16 | 9.00 | 5.81 | 8.30 | 17.83
Mar 23 master | 1.11 | 3.11 | 2.10 | 6.21 | 8.40
**Difference** | -2.05 | -5.89 | -3.61 | -2.09 | -9.43

Total Build Time

Branch | mediumWithJUnit | largeWithJUnit | single10000 | single25000 | single50000
------ | --------------- | -------------- | ----------- | ----------- | -----------
Mar 21 snapshot | 46.33 | 118.45 | 15.92 | 28.18 | 55.86
Mar 23 master | 43.55 | 112.32 | 11.77 | 25.30 | 48.82
**Difference** | -2.78 | -6.13 | -4.15 | -2.88 | -7.04

### Understand where test task is spending its time

Instrument Gradle to get a breakdown of how long each of the main activities in test start up take:

- Up-to-date check of the task inputs and outputs
- Scanning the test classes to identify test classes
- Starting the test process
- Initialisation of the test process, the time from test process start to when the first atomic test is started

Profile the above test build to identify hotspots and potential improvements. Generate further stories based on this.

Note: this goal for this story is only to understand the behaviour, not to fix anything.

#### Some results

Some initial profiling results: Some potential hotspots:

- Test execution generates many, many progress and logging events. The improvements for build startup would also improve this.
    - Could fix some hotspots in how messages are shipped between daemon process and test process, and between daemon process and daemon client.
- Report generation is expensive. Could potentially generate the xml and html concurrently (in the workers pool).
- Test class detection could benefit from some caching of extracted metadata. Implementation should extract some shared infrastructure for this from
  native and Java incremental compile.
- Directory scanning is expensive.
    - Up-to-date check.
    - Scan to detect test classes. The improvements to reuse directory scanning result could be used here to avoid the scanning.
    - Calculate class files hash. This should be removed, as it overlaps with `Test.candidateClassFiles`

A typical breakdown of the wall clock time spent by `test` with 1000 main and tests classes:

- 62ms, up-to-date check
- 11ms, start worker process
- 3246ms, detect and run tests (includes the above time)
    - 351ms, initialise worker process
        - 181ms, setup in worker
    - 443ms, detect test classes (mostly blocked waiting for worker to start)
    - 2930ms, run tests in worker
- 3ms, serialize binary results
- 226ms, generate XML reports
- 384ms, generate HTML reports
- 190ms, snapshot outputs
- 9ms, write task history

Miscellaneous profiling results:

- When excluding test execution time, up to 10% of remaining time is spent calling `getGenericReturnType` in decorators (fixed already)
- A large number of empty snapshots are generated when tasks are up-to-date. This could be optimized for memory and iteration. Experimental fix: https://github.com/gradle/gradle/commit/9946a56f225aa9f4007eb65f0cfb3274a718e140
- 20% of dependency resolution time in up-to-date build is spent in parsing the Ivy XML descriptor
- IDE plugin application consumes most of the build script (project) setup. The Eclipse and IDEA plugins trigger a lot of dynamic variable resolution (`conventionMapping`, `projectModel`, ...) which are very expensive (and not optimized) in Groovy. This is called even if, in the end, we will never call the IDEA or Eclipse tasks...

Latest hotspots on an up-to-date test execution build (aka, does nothing)

- **Dependency resolution** org.gradle.api.internal.artifacts.ivyservice.DefaultIvyContextManager.withIvy(Transformer) DefaultIvyContextManager.java 544ms	**9 %**
- groovy.lang.Closure.getPropertyTryThese(String, Object, Object) Closure.java 248ms	4 %
- **String interning** com.google.common.collect.Interners$WeakInterner.intern(Object) Interners.java 232ms	4 %
- java.util.LinkedList.toArray(Object[]) LinkedList.java 192ms	3 %
- org.gradle.api.internal.changedetection.state.CachingFileSnapshotter.snapshot(FileTreeElement) CachingFileSnapshotter.java 188ms	3 %
- groovy.lang.MetaClassImpl.invokeMissingProperty(Object, String, Object, boolean) MetaClassImpl.java 180ms	3 %
- java.lang.Class.getSimpleName() Class.java 160ms	3 %
- com.google.common.cache.LocalCache$LocalManualCache.getIfPresent(Object) LocalCache.java 148ms	3 %
- com.google.common.collect.Sets$SetFromMap.add(Object) Sets.java 124ms	2 %
- java.io.File.isDirectory() File.java 112ms	2 %
- java.lang.reflect.Method.getGenericReturnType() Method.java 104ms	2 % (already optimized, used to be 10%)
- org.gradle.api.internal.CompositeDynamicObject.setProperty(String, Object) CompositeDynamicObject.java 104ms	2 %
- org.gradle.initialization.ProjectPropertySettingBuildLoader.addPropertiesToProject(Project) ProjectPropertySettingBuildLoader.java 100ms	2 %
- org.gradle.internal.service.DefaultServiceRegistry.getFactory(Class) DefaultServiceRegistry.java 84ms	1 %
- org.gradle.internal.service.DefaultServiceRegistry$CompositeProvider.getService(DefaultServiceRegistry$LookupContext, DefaultServiceRegistry$TypeSpec) DefaultServiceRegistry.java 84ms	1 %
- java.lang.Class.isArray() Class.java (native) 80ms	1 %
