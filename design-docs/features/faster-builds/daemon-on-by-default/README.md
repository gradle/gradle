Improve and enable the daemon by default.

## Features

- [ ] [Daemon uses fewer developer machine resources](daemon-uses-fewer-resources)
- [ ] [Daemon is robust](daemon-is-robust)
- [ ] [Support all use cases that are supported by non-daemon execution](daemon-use-case-parity)
- [ ] [Fix Windows specific blockers](windows-blockers)
- [ ] Enable by default
    - Documentation
    - Adjust test suites and fixtures for this change
    - Remove single use daemon

TBD - could enable on non-windows platforms before fixing windows blockers.

## Candidate improvements
    
The following are some additional features to improve the developer experience using Gradle with the daemon. 
They probably don't block switching on the daemon and are most likely out of scope.
    
### Daemon cancels build when client is killed using ctrl-c

Developers frequently cancel the build using ctrl-c from the command-line. This causes the daemon process to exit, which is expensive as all useful state is discarded and the daemon 
is forced to warm up again on next build invocation.

Instead, ctrl-c should cause the daemon to cancel the build in progress, as if requested from the IDE. It should continue to fall back to exit when not possible
to gracefully stop the build.

### Daemon gracefully cancels build in more cases

The daemon implements cancellation by attempting to gracefully stop the build in progress. If it cannot do this within some timeout, the daemon exits to force the build to stop.
This is very expensive, as all useful state is discarded and the daemon is required to warm up again on the next build invocation. 

- Exec task should handle cancel request by killing child process [GRADLE-3083](https://issues.gradle.org/browse/GRADLE-3083)
- Test execution should handle cancel request

### Visibility of daemon status from the command line and IDE 

Allow the user to see which daemons are running, and what they are doing.

### Cross-version daemon management

Daemon management, such as `gradle --stop`, and visualisation, such as the previous feature, should consider daemons across all Gradle versions.

See [GRADLE-1891](https://issues.gradle.org/browse/GRADLE-1891)
