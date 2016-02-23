Improve and enable the daemon by default.

## Features

- [ ] [Daemon uses fewer developer machine resources](daemon-uses-fewer-resources)
- [ ] [Daemon is robust](daemon-is-robust)
- [ ] [Support all use cases that are supported by non-daemon execution](daemon-use-case-parity)
- [ ] [Fix Windows specific blockers](windows-blockers)
- [ ] Enable by default
    - Documentation
    - Adjust test suites and fixtures for this.
    - Remove single use daemon

## Candidate stories
    
The following are notes extracted from old feature specs. These are candidate features.
    
### Story - Daemon cancels build when client is killed using ctrl-c

ie don't kill the daemon when using `ctrl-c`.

### Story - improved cancellation

- Exec task should handle cancel request by killing child process [GRADLE-3083](https://issues.gradle.org/browse/GRADLE-3083)
- Test execution should handle cancel request
