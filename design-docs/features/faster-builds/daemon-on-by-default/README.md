Improve and enable the daemon by default.

## Features

- [ ] [Daemon uses fewer developer machine resources](daemon-uses-fewer-resources)
- [ ] [Daemon is robust](daemon-is-robust)
- [ ] [Fix Windows specific blockers](windows-blockers)
- [ ] Enable by default
    - Documentation
    - Adjust test suites and fixtures for this.
    - Remove single use daemon

## Candidate stories
    
The following are notes extracted from old feature specs. These are candidate features.
    
### Story - Daemon handles additional immutable system properties

Some system properties are immutable, and must be defined when the JVM is started. When these properties change,
a new daemon instance must be started. Currently, only `file.encoding` is treated as an immutable system property.

Add support for the following properties:

- The jmxremote system properties [GRADLE-2629](https://issues.gradle.org/browse/GRADLE-2629)
- The SSL system properties [GRADLE-2367](https://issues.gradle.org/browse/GRADLE-2637)
- 'java.io.tmpdir' : this property is only read once at JVM startup

### Story - Allow Ant task output to be captured

Often reported as 'log level changes are not honoured':

- [GRADLE-2828](https://issues.gradle.org/browse/GRADLE-2828)
- [GRADLE-2273](https://issues.gradle.org/browse/GRADLE-2273)
- [GRADLE-2271](https://issues.gradle.org/browse/GRADLE-2271)

Should be implemented by providing a way to ask that Ant task output be captured, by mapping the task output to some higher level or perhaps marking as 'do not filter'.

Also deprecate changing the log level from build logic. This kind of global state doesn't work well for parallel execution.

### Story - Daemon cancels build when client is killed using ctrl-c

ie don't kill the daemon when using `ctrl-c`.

### Story - improved cancellation

- Exec task should handle cancel request by killing child process [GRADLE-3083](https://issues.gradle.org/browse/GRADLE-3083)
- Test execution should handle cancel request

### Story - Correct up-to-date processing

- Handle enums and changing buildscript classpath [GRADLE-3018](https://issues.gradle.org/browse/GRADLE-3018)

### Story - Build logic prompts user for password

Replacement for `System.console` [GRADLE-2310](https://issues.gradle.org/browse/GRADLE-2310)
