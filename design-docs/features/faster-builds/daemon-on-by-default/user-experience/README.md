Address usability issues with the daemon

## Candidate Stories

### Daemon cancels build when client is killed using ctrl-c

Developers frequently cancel the build using ctrl-c from the command-line. This causes the daemon process to exit, which is expensive as all useful state is discarded and the daemon
is forced to warm up again on next build invocation.

Instead, ctrl-c should cause the daemon to cancel the build in progress, as if requested from the IDE. It should continue to fall back to exit when not possible
to gracefully stop the build.

#### Implementation
- Allow daemon to detach from terminal/console to prevent signals being sent to the daemon process when ctrl-c is sent.
- Change daemon logic to cancel the build when a client connection is dropped rather than stopping the daemon.
- Change daemon client logic to check for a daemon canceling a build and wait for 3 seconds for the cancel to complete.  If it doesn't complete in the timeout, send a stop request and start a new daemon.

#### Test Cases
- Start a build that will cancel immediately and kill the client.  Verify that the build is cancelled and that a new client connects to the original daemon.
- Start a build that will not cancel immediately and kill the client.  Start a new client before the cancel finishes and verify that when the cancel finishes, the client reconnects to the original daemon.
- Start a build that will not finish and kill the client.  Start a new client and verify that after the timout, the old daemon is killed and a new daemon is started.
- Start a build that will not complete and a build that will complete (results in idle daemon).  Cancel the build that will not complete, and start a new build.  Verify that the idle daemon is used rather than waiting on the daemon with the canceled build.

### Visibility of daemon status from the command line and IDE

Allow the user to see which daemons are running, and what they are doing.

### Daemon gracefully cancels build in more cases

The daemon implements cancellation by attempting to gracefully stop the build in progress. If it cannot do this within some timeout, the daemon exits to force the build to stop.
This is very expensive, as all useful state is discarded and the daemon is required to warm up again on the next build invocation.

- Exec task should handle cancel request by killing child process [GRADLE-3083](https://issues.gradle.org/browse/GRADLE-3083)
- Test execution should handle cancel request

### Cross-version daemon management

Daemon management, such as `gradle --stop`, and visualisation, such as the previous feature, should consider daemons across all Gradle versions.

See [GRADLE-1891](https://issues.gradle.org/browse/GRADLE-1891)
