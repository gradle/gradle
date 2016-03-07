Investigate and improve build configuration time, when build configuration inputs such as build scripts or Gradle or Java version have not changed.

Audience is developers that are using the Gradle daemon.

## Implementation plan

- Review and update performance tests to measure this and lock in improvements.
- Profile test builds and use results to select improvements to implement.
- Profile daemon client

### Potential improvements 

- Fix hotspots identified by profiling
- Replace usage of exceptions in decorated objects
- Faster rule execution for task configuration
- Make creation of project instances cheaper
- Faster startup by reducing fixed costs in daemon client and per build setup 
- Start progress logging earlier in build lifecycle to give more insight into what's happening early in the build 

## Stories

### Performance tests establish build startup baseline 

Ensure there is a performance test build that:

- Uses the current Java plugin
- Has many projects

Use this build in a performance test that:

- Runs `help`
- Uses the daemon

Tune the number of projects in the test build based on this. We're aiming for a build that takes around 10-20 seconds.

Note: review the existing test builds and _reuse_ an existing build and existing templates if possible. Should also reuse existing test execution performance test class if possible. 

### Write events to the daemon client asynchronously

Currently, events are written to the daemon client synchronously by the worker thread that produces the event.

Instead, queue the event for dispatch using a dedicated thread. Events should still be written in the order they are generated.

Daemon client should not need to be changed.

Add some more test coverage for logging from multiple tasks and threads, and for progress logging from multiple projects and tasks, for parallel execution.
These tests should run as part of the daemon and non-daemon test suites.

### Don't hash the build script contents on each build

Use the same strategy for detecting content changes for build scripts, as is used for other files.

Should use `CachingFileSnapshotter` for the implementation, if possible. Could potentially be backed by an in-memory `PersistentStore`, reusing the instance
created by `GlobalScopeServices.createClassPathSnapshotter()`. An additional refactoring could make this a persistent store. 

### Reuse build script cache across builds

Currently, the cache for a given build script is closed at the end of the build.

Investigate options for reusing this across builds, when the build script has not changed.

### Understand where build startup is spending its time

Profile the daemon and Gradle client using the above test build to identify hotspots and potential improvements. Generate further stories based on this.

Note: this goal for this story is only to understand the behaviour, not to fix anything.
