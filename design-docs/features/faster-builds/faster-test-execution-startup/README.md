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

- `build/classes/test` is scanned 4 times : one reason for this is that output snapshots resolve the same file collection twice, once when calling `getFiles` and then when creating the snapshot.
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

- when excluding test execution time, up to 10% of remaning time is spent calling `getGenericReturnType` in decorators (fixed already)
- A large number of empty snapshots are generated when tasks are up-to-date. This could be optimized for memory and iteration. Experimental fix: https://github.com/gradle/gradle/commit/9946a56f225aa9f4007eb65f0cfb3274a718e140

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
