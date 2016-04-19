
Support all use cases that are supported by non-daemon execution

## Implementation plan

## Candidate stories

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

#### Implementation
- Add a `lifecycleLogLevel` setting to AntBuilder so that the user can set the Ant message priority that corresponds to the lifecycle logging level.
Any priorities higher than this that would normally not be logged at least at lifecycle will be set to lifecycle.  Any priorities lower than this
that would normally be logged at lifecycle or above will be set to info.
- Deprecate `LoggingManager.setLevel()` and introduce a new internal method to allow legitimate internal calls to set the logging level.  The existing public
`setLevel` method will call the internal method but also nag the user with a deprecation message.

#### Test Cases
- Setting lifecycleLogLevel to something lower than WARN causes that priority and any higher priorities to log at lifecycle or above.
- Setting lifecycleLogLevel to ERROR causes WARN to be logged at INFO level.
- Not setting the lifecycleLogLevel causes the default mapping to be used.
- Setting the log level from a task causes a deprecation message.
- Setting the log level from the project object causes a deprecation message.

### Story - Correct up-to-date processing

- Handle enums and changing buildscript classpath [GRADLE-3018](https://issues.gradle.org/browse/GRADLE-3018)

## Non-blocking

### Story - Build logic prompts user for password

(out of scope for enabling daemon by default)

Replacement for `System.console` [GRADLE-2310](https://issues.gradle.org/browse/GRADLE-2310)
