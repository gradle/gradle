
- Fix lifecycle and diagnostic issues that prevent the daemon to be enabled by default
    - Clean up cached `ClassLoaders` that cannot be used again.
- Fix Windows specific blockers
- Switch on by default
    - Documentation
    - Adjust test suites and fixtures for this.
    
### Daemon robustness    

- All client and daemon reads on the connection should have a timeout.
- Daemon should exit when its entry is removed from the registry.
    
## Feature - Daemon usability improvements

### Story - Build script classpath can contain a changing jar

Fix ClassLoader caching to detect when a build script classpath has changed.

Fix the ClassLoading implementation to avoid locking these Jars on Windows.

### Story - Can clean after compiling on Windows

Fix GRADLE-2275.

### Story - Prefer a single daemon instance

Improve daemon expiration algorithm so that when there are multiple daemon instances running, one instance is
selected as the survivor and the others expire quickly (say, as soon as they become idle).

### Story - Daemon handles additional immutable system properties

Some system properties are immutable, and must be defined when the JVM is started. When these properties change,
a new daemon instance must be started. Currently, only `file.encoding` is treated as an immutable system property.

Add support for the following properties:

- The jmxremote system properties (GRADLE-2629)
- The SSL system properties (GRADLE-2367)
- 'java.io.tmpdir' : this property is only read once at JVM startup

### Story - Daemon process expires when a memory pool is exhausted

Improve daemon expiration algorithm to expire more quickly a daemon whose memory is close to being exhausted.

### Story - Cross-version daemon management

Daemon management, such as `gradle --stop` and the daemon expiration algorithm should consider daemons across all Gradle versions.

### Story - Reduce the default daemon maximum heap and permgen sizes

Should be done in a backwards compatible way.

# Daemon feature is “usable” when under memory pressure

Currently, the daemon has serious problems when memory pressure occurs.
When under pressure, the daemon process exhibits GC thrash.
Please see [this forum post](http://forums.gradle.org/gradle/topics/gradle_daemon_becomes_very_slow_when_the_heap_is_nearly_out_of_memory_its_running_full_gcs_almost_back_to) for a discussion.

One hypothesis for this is the use of weak reference caches, particularly in the Groovy metaclass system where meta class instances are held in a weak reference cache.
Note that this is not necessarily a problem with the daemon, as it would also apply to the non daemon case.
However, it is exacerbated by the daemon leaking memory, thereby increasing the chance of a memory pressure situation occurring.

The correct outcome would be for the build to fail quickly instead of hanging in GC thrash limbo.
This could be done by either detecting or predicting GC thrash and terminating early.

# Memory leaks

Memory leaks are unavoidable because:

- Gradle runs 3rd party code that may incur mem leaks
- Gradle ships with 3rd party tools, many of them quite large (groovy) and they may contain mem leaks
- Gradle uses jdk and it can have bugs that lead to mem leaks ;)
- Gradle itself can have mem leaks

1. First front of a battle against the leaks is fixing them in tools we control and reporting bugs to tools we don't control.
2. Second front is to make the daemon smarter. Daemon should know the footprint and perform actions based on that knowledge.
   Those actions could be: exit/expire daemon quickly, restart eagerly, inform the user about memory problem, etc.

# Implementation plan

## The user is aware of daemon health

Let the user be proud of the daemon, of how many builds it happily served and the operational uptime.
Let the user be aware of daemon performance so that he can map the performance to things like:
plugins, build logic, environment or build invocations.
Consumption of this information may lead to interesting discoveries and valuable feedback for the Gradle team.
Help building stronger confidence in the daemon and its smartness by demonstrating
in every build that the daemon is able to monitor its own health.

### User visible changes

- When building with the daemon there is an elegant lifecycle message informing about the daemon status

"Starting build in new daemon [memory: 30.4 MB]"
"Executing 2nd build in daemon [uptime: 2.922 secs, performance: 91%, memory: 100% of 28.8 MB]"

- The message is only shown when 'org.gradle.daemon.performance.info' gradle property is enabled.
Example gradle.properties: 'org.gradle.daemon.performance.info=true'

### Test coverage

- First build presents "Starting build..." message
- Subsequent builds present "Executing x build..." message

## Prevent memory leaks make daemon unusable, ensure high daemon performance

Allow using daemon everywhere and always, even for CI builds. Ensure stability in serving builds.
Prevent stalled builds when n-th build becomes memory-exhausted and stalls.

Continuous tracking of daemon's performance allows us to expire the daemon when it's performance drops below certain threshold.
This can ensure stability in serving builds and avoid stalled build due to exhausted daemon that consumed all memory.

### User visible changes

- daemon is stopped after the build if the performance during the build was below certain threshold
- the default expire threshold is 85%
- threshold can configured in gradle.properties via 'org.gradle.daemon.performance.expire-at=85%'
- the feature can be switched off in gradle.properties file by specifying: 'org.gradle.daemon.performance.expire-at=0%'
- when daemon is expired due to this reason, a lifecycle message is presented to the user

### Coverage

- integration test that contains a leaky build. The build fails with OOME if the feature is turned off.

## Prevent daemon become unresponsive due to gc thrashing

# Ideas

- Daemon automatically prints out gc log events to the file in daemon dir. Useful for diagnosing offline.
- Daemon writes gc log events and analyzes them:
    - Understands and warns the user if throughput is going down
    - Knows when gc is about to freeze the vm and and exits eagerly providing decent message to the user
    - tracks memory leaks
- Daemon scales memory automatically by starting with some defaults and gradually lowering the heap size
- Daemon knows why the previous daemon exited. If it was due to memory problems a message is shown to the user.

# Open issues

This section is to keep track of assumptions and things we haven't figured out yet.
