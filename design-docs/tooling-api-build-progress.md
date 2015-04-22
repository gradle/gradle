# Tooling API client listens to build execution progress

## Listening to test execution progress

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


### Open issues

- It is currently not possible to listen for tests in `buildSrc`, probably because `buildSrc` is not seen as a project from the model.


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

- some test coverage to prove that the events are live, current tests prove only that they’re available at the end of the build

- what’s the relationship between tasks and test progress events?

Progress events have a descriptor, which references a parent descriptor. This parent descriptor may correspond to a task descriptor.

- are we going to expose all build operations at this point, or just tasks?

Tasks are going to be exposed first. Further operations may be added in the future based on feedback or specific needs.

- what happens when the build is cancelled? do i receive an ‘operation cancelled’ event for each operation in progress? or do i stop receiving events?

- we should distinguish between tests that were up-to-date vs tests that were disabled. in the former case, the output of the task is usable, where as in the later case it is undefined

- do we expose tests and tasks from nested builds invoked using GradleBuild task or the tooling API?

- what do we expose about the task in the operation descriptor?

- better handling of a broken listener. currently the test proves that an exception (could be pretty much any exception) is thrown and says nothing about what information is conveyed to the user about what happened, or what happens to the build or the daemon hosting the build

- cancelling a build from a listener

It is possible to cancel a build from a listener. Cancellation is however limited to what is supported in Gradle core. In particular, if execution is cancelled during a test task, the whole task
is executed. Only the next task will be cancelled.

- stopping the tooling api from a listener

- what happens when the daemon crashes? 