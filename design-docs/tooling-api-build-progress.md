# Tooling API client listens to build execution progress

## Listening to test execution progress

### Listening to tests executed from the tooling API

It should be possible to listen to test execution progress from the Tooling API. This includes:

- test events from tests in the Gradle build : implemented
- test events from tests in the `buildSrc` directory. This is currently not possible because `buildSrc` doesn't appear in the model
and is not seen as a project. It should however be easier to implement than the next items.
- events should be received live, while the build is running, and not as a batch when the build is over

Listening to test events in `buildSrc` requires a way to get access to the `GradleInternal` instance of
the project generated to compile the build classpath. This `GradleInternal` instance uses its own `ListenerManager`,
so when the test progress listener is added (in `BuildModelActionRunner`), the listener manager is not the one of
`buildSrc`.

### Listening to progress events from a nested build

- `buildSrc`
- Run using `GradleBuild` task
- Run using `CompareGradleBuilds` task
- Run using the tooling API

### Listening to tests executed from the tooling API within a build

It is possible for a user to create a task that will use the tooling API to execute a build/tests. This
differs from executing a `GradleBuild` task because the latter will not use the tooling API, hence targets
the same Gradle version as the version currently executed. The goal here is therefore to execute a
sub-build which may target a different version of Gradle.

It should be possible to listen to test events from a build executed in such a way. That is to say:

Tooling API -> gradle build -> gradle task calling tooling API -> tests

The events should be sent back from the tests to the original tooling API.

### Build cancellation and event types

It should be possible to cancel a build from the tooling API, including from a test listener. When a build is cancelled:

- listeners should receive a "cancelled" event for the test task
- listeners should receive a "cancelled" event for the build
- listeners should be able to make a difference between a "cancelled" event and a "finished" event
- listeners should be able to make a difference between a "skipped" event and a "cancelled" event
- listeners should be able to make a difference between a "skipped" event and an "up-to-date" event

### Test descriptors

A test descriptor depends on the underlying test framework. It is not necessarily a JVM test, hence doesn't necessarily
refer to a test class, a test suite or a test method. Descriptors should allow the client to build a hierarchy of
events:

- a descriptor provides a human readable, non localized, description of the event
- a descriptor may have a parent descriptor
- a descriptor may refer to a test descriptor, a task descriptor or implementation detail descriptors (like workers)


### Error handling

If the daemon disappears unexpectedly (crashed, killed) :

- listeners should receive a "failure" event for the build
- the failure outcome should include a "daemon disappeared unexpectedly" error

If an error occurs in a test listener:

- event listening should not be interrupted
- build should not be interrupted
- task should not be interrupted

If the tooling API is shutdown from a test listener:

- listeners should receive a "failure" event for the build
- the failure outcome should include a "tooling API disappeared unexpectedly" error

### User experience improvements

It should be possible to build the list of tests to be executed before they are actually executed. When the underlying
test framework allows it:

- listeners should receive a "pending" event for tests that are going to be executed but are not started
- "pending" events should be received before the first test is started. Note that this is not always possible, nor a requirement. If tests are executed in parallel or on different machines,
it is acceptable that the list of "pending" tests grows as new clients are connected. Some frameworks may also dynamically generate tests from existing tests.

The idea here is more to allow the UI to warn the user about upcoming tests and provide some idea about the overall progress.


### Test cases

- Can receive test events when running a build : done
- Can receive test events when requesting a model :done
- Can receive test events when running a custom build action : ?
- Can receive test events from multiple test tasks : done
- Can receive test events when tests run in parallel : ?
- Useful behaviour when a client provided test listener throws an exception
- Useful behaviour when a client provided progress listener throws an exception
- Can receive events from tests in `buildSrc`
- Can receive events from tests in builds run using `GradleBuild`
- Can receive events from tests in builds run from build using tooling API : ?
- Receives 'finished' test events when build is cancelled or daemon crashes
- Receives 'finished' progress events when build is cancelled or daemon crashes
- Receives test events live, as the tests are executed


## Listening to task execution progress

- Can receive task events when running a build.
- Can receive task events when requesting a model.
- Can receive task events when running a custom build action.
- Can receive task events for multiple tasks types.
- Can receive task events when tasks run in parallel.
- Useful behaviour when a client provided task progress listener throws an exception
- Can receive events from tasks in `buildSrc`
- Can receive events from tasks in builds run using `GradleBuild`
- Can receive events from tasks in builds run from build using tooling API
- Receives 'finished' task events when build is cancelled or daemon crashes


## Open issues

- are we going to expose all build operations at this point, or just tasks?

Tasks are going to be exposed first. Further operations may be added in the future based on feedback or specific needs.
