### Story: Client uses internal API to request cancellation of long running operation (DONE)

This story adds an API to allow a client to request the cancellation of a long running operation. For this story, the
API will be internal. The API will be made public in a later story.

#### Implementation plan

1. Add API to the client.
2. Add new tooling API protocol interfaces to support cancellation.
3. The provider implementation simply acknowledges the cancellation request, but does not actually cancel the operation. This is added in a
subsequent story.

#### Test cases

- Client can cancel operation for target Gradle version that supports cancellation:
    - Building model
    - Running tasks
    - Running build action
- Client receives reasonable error messages when attempting to cancel operation for target Gradle version that does not support cancellation.
- Client can cancel operation after operation has completed:
    - Successful operation
    - Failed operation
- Client cancels operation from `ResultHandler`
- Client can cancel operation before started and it won't be executed:
    - Building model
    - Running tasks
    - Running build action

### Story: Task graph execution is aborted when operation is cancelled (DONE)

In this story, task graph executor no longer starts executing new tasks when operation is cancelled.

#### Test cases

- Client cancels a build before the start of some task(s) execution
    - Some time after requesting, the client receives a 'build cancelled' exception and the daemon is still running and the task is not executed.

### Story: Project configuration is aborted when operation is cancelled

In this story, no further projects should be configured after the operation is cancelled. Any project configuration
action that is currently executing should continue, similar to the task graph exececution.

Implementation-wise, change `DefaultBuildConfigurer` to stop configuring projects once the cancellation
token has been activated.
