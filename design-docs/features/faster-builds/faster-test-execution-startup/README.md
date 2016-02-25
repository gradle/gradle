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

TBD - Need to specify the Maven command-line invocation.

TBD - Potentially add a cross build test case to allow this comparison to be automated.

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

A breakdown of the wall clock time spent by `test` with 1000 main and tests classes:

-   74ms, up-to-date check
-  354ms, start and connect to worker process
- 3023ms, detect and run tests
-    6ms, serialize binary results 
-  504ms, generate XML and HTML reports
-  236ms, detect output files and write task history
