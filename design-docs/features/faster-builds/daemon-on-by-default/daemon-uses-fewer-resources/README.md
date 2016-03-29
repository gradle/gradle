# Daemon uses fewer resources on developer machine.

## Feature: Expire daemons in a smarter way

### Story: Expire daemons from least recently used builds based on total number of daemons

A very simple expiration strategy by limiting the the number of daemon processes running in parallel on a machine.

#### Implementation

- Define max number of daemons (can be referenced by internal system property ('org.gradle.daemon.expiration.maxnumber', defaulting to __2__).
- Track last daemon usage in daemon registry.
- When requesting daemon process number > threshold, stop the least recently used daemon process
    1. iterate over idle daemons and stop least recently used ones till threshold is met.
    2. iterate over busy daemons and request graceful stop of least recently used ones till threshold is met.

##### Test Coverage

- Running two daemons alternately on a build multiple times does not expire daemons
- Initiating a third daemon process stops the oldest idling daemon process.
- TBD.

##### Open questions

- What to do if already max number of daemons are _Busy_ running a build.
    possible Options:
    - warn and stop current build
    - stop initiated daemon after the build (no long running process)
    - run build and request graceful stop of _oldest_ daemon

### Story: Expire daemons from least recently used builds based on accumulated total heap size

#### Implementation

- Define total max heap memory used by daemons (can be referenced by internal system property ('org.gradle.daemon.expiration.totalheapsize', defaulting to __4096m__).
- When invoking daemon process and memory threshold is reached

##### Test Coverage

- TBD

### Story: Expire daemons from least recently used builds based on accumulated total percentage of available heap size

##### Implementation

##### Open questions

- How to gather these Statistics of current machine

### Story: Apply expiration metrics across daemons from all versions >= 2.14)

#### Implementation


## Feature: Reduce wasted heap in the daemon

## Feature: Reduce default heap sizes for the daemon

## Candidate improvements

### Prefer a single daemon instance

Improve daemon expiration algorithm so that when there are multiple daemon instances running, one instance is
selected as the survivor and the others expire quickly.

See [GRADLE-1890](https://issues.gradle.org/browse/GRADLE-1890)

- Simple algorithm. Perhaps keep the most recently used daemon and expire all other daemons once they become idle
    - Possibly have a short grace period
    - Potentially keep a daemon per build (ie root directory), as much of the daemon state is build specific
    - Potentially scale expiration based on memory pressure on the machine
- Spike to investigate how algorithm behaves, when switching between builds, Java versions, etc.
- Tune the default TTL
- Allow the test fixtures to tune this for the daemon test suite, so that a daemon per parallel test worker is used

### Reduce the default daemon maximum heap and permgen sizes

Should be done in a backwards compatible way.

### Cross-version daemon expiration

The daemon expiration algorithm should consider daemons across all Gradle versions.

## Discard unusable ClassLoaders and state early

The daemon maintains ClassLoaders and associated state that it will never reuse. These should be discarded as soon as it is known
to be unusable. Also close the associated file system caches and release in-heap state.
