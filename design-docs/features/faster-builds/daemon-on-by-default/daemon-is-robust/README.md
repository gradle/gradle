Fix robustness and diagnostic issues that prevent the daemon to be enabled by default

## Implementation plan

- Add daemon stability tests
    - existing stability related tests in
        - DaemonPerformanceMonitoringIntegrationTest
        - DaemonHealthLoggingIntegrationTest
- Review and tune the existing disabled health monitoring
- Add diagnostics
- Spike additional strategies for health monitoring
    - collect gc statistics
    - discuss expose health via jmx
    - discuss log health status in internal log format

## Candidate stories

- Add daemon stability tests
- All client and daemon reads on the connection should have a timeout.
- Daemon should exit when its entry is removed from the registry [GRADLE-1763](https://issues.gradle.org/browse/GRADLE-1763)
- Client should be more forceful when stopping daemons [GRADLE-1638](https://issues.gradle.org/browse/GRADLE-1638)

### Story - Daemon process expires when a memory pool is exhausted

Improve daemon expiration algorithm to expire more quickly a daemon whose memory is close to being exhausted.

- See [GRADLE-2193](https://issues.gradle.org/browse/GRADLE-2193)
- See [GRADLE-1839](https://issues.gradle.org/browse/GRADLE-1839)

## Feature - Daemon is “usable” when under memory pressure

Currently, the daemon has serious problems when memory pressure occurs.
When under pressure, the daemon process exhibits GC thrash.
Please see [this forum post](http://forums.gradle.org/gradle/topics/gradle_daemon_becomes_very_slow_when_the_heap_is_nearly_out_of_memory_its_running_full_gcs_almost_back_to) for a discussion.

One hypothesis for this is the use of weak reference caches, particularly in the Groovy metaclass system where meta class instances are held in a weak reference cache.
Note that this is not necessarily a problem with the daemon, as it would also apply to the non daemon case.
However, it is exacerbated by the daemon leaking memory, thereby increasing the chance of a memory pressure situation occurring.

The correct outcome would be for the build to fail quickly instead of hanging in GC thrash limbo.
This could be done by either detecting or predicting GC thrash and terminating early.

### Memory leaks

Memory leaks are unavoidable because:

- Gradle runs 3rd party code that may incur mem leaks
- Gradle ships with 3rd party tools, many of them quite large (groovy) and they may contain mem leaks
- Gradle uses jdk and it can have bugs that lead to mem leaks ;)
- Gradle itself can have mem leaks

1. First front of a battle against the leaks is fixing them in tools we control and reporting bugs to tools we don't control.
2. Second front is to make the daemon smarter. Daemon should know the footprint and perform actions based on that knowledge.
   Those actions could be: exit/expire daemon quickly, restart eagerly, inform the user about memory problem, etc.

### Implementation plan

### The user is aware of daemon health

Let the user be proud of the daemon, of how many builds it happily served and the operational uptime.
Let the user be aware of daemon performance so that he can map the performance to things like:
plugins, build logic, environment or build invocations.
Consumption of this information may lead to interesting discoveries and valuable feedback for the Gradle team.
Help building stronger confidence in the daemon and its smartness by demonstrating
in every build that the daemon is able to monitor its own health.

##### User visible changes

- When building with the daemon there is an elegant lifecycle message informing about the daemon status

"Starting build in new daemon [memory: 30.4 MB]"
"Executing 2nd build in daemon [uptime: 2.922 secs, performance: 91%, memory: 100% of 28.8 MB]"

- The message is only shown when 'org.gradle.daemon.performance.info' gradle property is enabled.
Example gradle.properties: 'org.gradle.daemon.performance.info=true'

#### Test coverage

- First build presents "Starting build..." message
- Subsequent builds present "Executing x build..." message

### Prevent daemon with a leak from reaching a point where gc is thrashing

We need to monitor garbage collection behavior and stop the daemon when a leak appears to be exhausting tenured heap space.  In order to do so, we want to look at two metrics:

- Garbage collection rate - rate at which the tenured heap is being garbage collected
- Tenured heap usage - the amount of space allocated in the tenured heap _after_ garbage collection

We need to check these values after a build has completed (to decide whether a new build should occur) as well as periodically to catch scenarios where a runaway thread is leaking memory outside of a build.  If thresholds for both values have been tripped, we should expire the daemon either immediately or after the build if a build is running.

#### Implementation

- Register a polling mechanism to check garbage collection statistics once a second.  Gather:
  - Garbage collection count (from GarbageCollectorMXBean)
  - Tenured memory pool usage after garbage collection (from MemoryPoolMXBean)
  - Timestamp of the current check
- Maintain a window of up to 20 measurements (i.e. the state of the garbage collector over the last 20 seconds)
- At the end of a build or during a periodic check, calculate the GC rate and average tenured heap usage from the window of events.  If both thresholds are crossed, request the daemon should stop.
- For the Sun JVM, the threshold should be a GC rate of 1.5/s and 80% usage of tenured space.
- For the IBM JVM, the threshold should be a GC rate of 1.5/s and 70% usage of tenured space.
- For other JVMs, we won't implement this check.

#### Test coverage

- Detects fast leaks in builds with both a small (200m) and large heap (1024m) and stops daemon.
- Detects slow leaks in builds with both a small (200m) and large heap (1024m) and stops daemon.
- For a build that leaks without tripping the thresholds, the daemon is not stopped.
- User can specify a threshold for gc rate and tenured heap usage

### Prevent daemon with a Perm Gen leak on <=JDK7 from reaching a point where gc is thrashing

We need to monitor garbage collection behavior and stop the daemon when a leak appears to be exhausting perm gen space.  In order to do so, we should only need to look at the usage of the perm gen memory pool.  There should not be a ton of churn on this pool, so simply measuring the usage after garbage collection should be enough to decide if there is a perm gen leak.

We need to check this value after a build has completed (to decide whether a new build should occur) as well as periodically to catch scenarios where a runaway thread is leaking memory outside of a build.  If thresholds for both values have been tripped, we should expire the daemon either immediately or after the build if a build is running.

#### Implementation

- Register a polling mechanism to check perm gen memory pool usage once a second.
- Maintain a window of up to 20 measurements (i.e. the state of the perm gen space over the last 20 seconds)
- At the end of a build or during a periodic check, calculate the average perm gen usage from the window of events.  If the threshold is crossed, request the daemon should stop.
- For the Sun JVM, the threshold should be 85% usage of perm gen space.
- Most other JVMs don't use perm gen space, so we won't implement this check for anything other than the Sun JVM.

#### Test coverage

- Detects a perm gen leak in a build and expires the deamon at the end of a build
- User can specify a threshold for perm gen heap usage

### Detect when gc is thrashing and premptively stop the daemon

We need to monitor garbage collection behavior and forcefully stop the daemon when it appears that the garbage collector is actively thrashing.  In order to do so, we want to look at two metrics:

- Garbage collection rate - rate at which the tenured heap is being garbage collected
- Tenured heap usage - the amount of space allocated in the tenured heap _after_ garbage collection

We need to check these values periodically during a build.  If thresholds for both values have been tripped, we should forcefully stop the daemon to prevent an unresponsive state and notify the user.

This story depends on the story for making daemon health visible.

#### Test coverage

- Detects gc thrash in a build that agrressively allocates/leaks memory and preemptively stops the daemon with a sensible error message.

#### Implementation

- Leverage the periodic garbage collection statistic checks to gather information about GC rate and average tenured usage. 
- As part of the check, if both thresholds are crossed, immediately force stop the daemon, sending a message to the user.
- For the Sun JVM, the threshold should be a GC rate of 20/s and 90% usage of tenured space.
- For the IBM JVM, the threshold should be a GC rate of 20/s and 80% usage of tenured space.
- For other JVMs, we won't implement this check.

### Prevent memory leaks from making the daemon unusable, ensure high daemon performance

Allow using daemon everywhere and always, even for CI builds. Ensure stability in serving builds.
Prevent stalled builds when n-th build becomes memory-exhausted and stalls.

Continuous tracking of daemon's performance allows us to expire the daemon when it's performance drops below certain threshold.
This can ensure stability in serving builds and avoid stalled build due to exhausted daemon that consumed all memory.

#### User visible changes

- daemon is stopped after the build if the performance during the build was below certain threshold
- the default expire threshold is 85%
- threshold can configured in gradle.properties via 'org.gradle.daemon.performance.expire-at=85%'
- the feature can be switched off in gradle.properties file by specifying: 'org.gradle.daemon.performance.expire-at=0%'
- when daemon is expired due to this reason, a lifecycle message is presented to the user

#### Coverage

- integration test that contains a leaky build. The build fails with OOME if the feature is turned off.

### Story - Internally Expose Basic Daemon Information
Some basic daemon status information is made, internally, available via the service registry. 

1. The number of builds executed by the daemon
1. The idle timeout of the daemon
1. The number of running daemons
1. The time at which the daemon was started

#### Implementation

1. Introduce the following interface
  ```java
  package org.gradle.launcher.daemon.server.scaninfo;

  public interface DaemonScanInfo {
      int getNumberOfBuilds();
      long getStartedAt();
      long getIdleTimeout();
      int getNumberOfRunningDaemons();
  }
  ```
  With a default implementation with references to `org.gradle.launcher.daemon.server.health.DaemonStats` and `org.gradle.launcher.daemon.registry.DaemonRegistry`
1. Expose `DaemonScanInfo` from `DaemonServices`
1. `org.gradle.launcher.daemon.server.health.DaemonStats` provides getters for `buildCount` and a new attribute `startTime` (the time the daemon was started)
1. `org.gradle.launcher.daemon.bootstrap.DaemonMain` passes the daemon start time to `DaemonServices`. This is not quite exactly the point in time that the daemon starts 
 but may be close enough.
1. `DaemonServices` is responsible for instantiating `org.gradle.launcher.daemon.server.health.DaemonScanInfo` e.g.
  ```java
    protected DaemonScanInfo createDaemonInformation(DaemonStats daemonStats) {
        return DefaultDaemonScanInfo.of(daemonStats, configuration.getIdleTimeout(), get(DaemonRegistry.class));
    }
  ```
1. `DaemonScanInfo` can be accessed via the service registry 
  ```groovy
     import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo
     DaemonScanInfo info = project.getServices().get(DaemonScanInfo)
  ```

#### Coverage
- A DaemonIntegrationSpec which verifies all 4 data points 
- The number of builds is correctly incremented when a daemon runs more than one build
- The number of running builds is backed by the `org.gradle.launcher.daemon.registry.DaemonRegistry#getAll`
- Works when the daemon is ran in the foreground `--foreground`
- Works when the daemon is run with `--continuous`

### Story - Internally register a listener for daemon expiration events

#### Implementation

1. Add `DaemonExpirationListenerRegistry` as a service via `org.gradle.launcher.daemon.server.DaemonServices`
1. `DaemonExpirationListenerRegistry` takes the same `ListenerManager` used by `DaemonHealthCheck`
1. Clients can register a listener as follows: 
  ```groovy
   def registry = project.getServices().get(org.gradle.launcher.daemon.server.DaemonExpirationListenerRegistry)
   registry.register(new DaemonExpirationListener() {
      @Override
      public void onExpirationEvent(org.gradle.launcher.daemon.server.DaemonExpirationResult result) {
          println "onExpirationEvent fired with: \${result.getReason()}"
      }
  })
  ```

#### Coverage
- An integration test which:
   - Registers a dummy `org.gradle.launcher.daemon.server.DaemonExpirationStrategy` which aways returns a `DaemonExpirationResult`
   - Registers a `DaemonExpirationListener` which prints the `result` of `DaemonExpirationResult`
   - Verifies the console output by the above `DaemonExpirationListener`
- Works when the daemon is ran in the foreground `--foreground`
- Works when the daemon is run with `--continuous`
