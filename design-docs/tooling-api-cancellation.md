## Feature: Tooling API client cancels a long running operation

Add possibility to cancel execution of a long running operation using a `CancallableToken`.
This `CancallableToken` is produced by `CancellableTokenSource` and can be used with several operations if needed.

    interface CancellationToken {
        boolean canBeCancelled();
        boolean isCancellationRequested();
    }

    public class CancellationTokenSource {
        public void cancel() ...
        public CancellationToken token() ...
    }

    interface LongRunningOperation {
        ...
        @Incubating
        LongRunningOperation withCancellationToken(CancellationToken cancellationToken);
    }

In the API `CancellationTokenSource` is client side part with a factory method to create (provided part of the contract) `CancellationToken`. 
Client can pass this token to one or more operations and can call `cancel()` at any time.
The method does 'best-effort' to request stop for the performed operation assuming that the provider side cooperates and the implementation returns immediately.
To enable this cooperation operation implementation can query `CancellationToken.isCancellationRequested()`.
When provider successfully cancels the operation during its processing the client will be notified using `BuildCancelledException` passed to `ResultHandler.onFailure()` callback (another addition to API).
Provider ignores cancel requests after operation is finished.
Last call to 'LongRunningOperation.withCancellationToken` wins and each operation can use only one token.

Open questions:

Calling `cancel()` when the provider does not support it can 

- be a no-op (log that the request is ignored)
- throw an exception
- method can be changed to return boolean flag signaling if the cancel request was acknowledged.

### Story: Daemon exits when operation is cancelled

This story adds a basic cancellation implementation. The behaviour is similar to the case where the command-line client is killed, where the daemon
process simply exits. This behaviour will be improved later.

#### Implementation plan

1. Provider implementation forwards the cancellation request to the daemon.
2. The daemon uses `DaemonStateControl.requestForcefulStop()` to terminate the build. (This is achieved by calling `DaemonClient.stop()` from toolingApi provider/daemon client.)
3. Forward a 'build cancelled' exception to the client.

#### Test cases

- Client cancels a long build
    - Some time after requesting, the client receives a 'build cancelled' exception and the daemon is no longer running.

#### Open issues

- Daemon client should send cancel message on same connection as it used to start the build.
- User should not receive 'daemon disappeared' error message and log file contents on forceful stop.

### Story: Daemon waits short period of time for cancelled operation to complete

In this story, the daemon attempts to terminate a cancelled build more gracefully, by waiting for a short period of time for the build to complete.

#### Implementation plan

1. When `DaemonStateControl.requestForcefulStop()` is called, the daemon waits for 10 seconds (say) for the build to complete. If the build
completes, then do not exit. If the build does not complete in this time, exit the process.

#### Test cases

- Client cancels a short build
    - Some time after requesting, the client receives a 'build cancelled' exception and the daemon is still running.
    - Should use fixture to probe the daemon logs to determine that it has seen the request and terminated the build cleanly.
    - Client continues to get build output during this time.
    - Verify for cancellation of project configuration and task graph execution.
- Command-line client runs a short build and is killed.
    - Some time after this the daemon finishes the build and continues to run.
    - Should use fixture to probe the daemon logs to determine that it has seen the event and terminated the build cleanly.
- Extend the existing daemon termination tests to use fixture to probe the daemon logs to determine that the daemon has decided to exit.

### Story: Gradle distribution download is aborted when operation is cancelled

In this story, the Gradle distribution download is stopped when operation is cancelled.

#### Test cases

- Client requests to execute an operation (build, model) using a distribution that needs to be downloaded and cancels the operation during the download
    - Some time after requesting, the client receives a 'build cancelled' exception and download is terminated and partial downloads are removed
    - Verify this behavior for regularly processed downloads and for stalled downloads waiting on blocking I/O.

### Story: Tooling API client receives BuildCancelledException as the result of a cancelled operation

This story ensures that consistent behaviour is seen by the client as the result of a cancelled operation.

#### Open issues

- Need consistent handling of cancellation exception in `BuildModelAction` and `ClientProvidedBuildAction`. Should push this closer to the provider entry point.
- `DaemonBuildActionExecuter` converts exception to pass across to tooling api consumer. Should be done in a wrapper that is also used when embedded.
- Enable int tests in embedded mode, except for the forceful stop int test.
- Verify exception message in int tests is consistent for all cases.

### Story: Build user is informed that build was cancelled

The logging output of the build should inform the build user that the build was cancelled (rather than failed because of an exception).

- 'This build was cancelled' message instead of 'build failed with an exception' message when no failures have occurred.
- 'build failed with an exception' when some tasks have failed prior to build cancellation (eg when running with `--continue`).

### Story: Build action receives exception when operation is cancelled

In this story, a `BuildAction` receives an exception when it is using or uses a method on `BuildController` when operation is cancelled.

#### Open issues

- `DefaultBuildController` throws this exception only on entry to the method, not when cancelled later

## Later stories

### Story: Tooling API client receives feedback after cancellation is requested

- Add some mechanism to inform the client when cancellation is or is not available
- Add some mechanism to inform the client about the state of a cancellation request.

### Story: Test execution is aborted when operation is cancelled

### Story: Task graph assembly is aborted when operation is cancelled

### Story: Model rule execution is aborted when operation is cancelled

In this story, the `ModelRegistry` implementation stops executing rules when operation is cancelled.

### Story: Nested operations started using tooling API are cancelled when outermost operation is cancelled

When build logic uses the tooling API to start further operations, these nested operations should also be cancelled.
