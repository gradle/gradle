# Daemon uses fewer resources on developer machine.

## Feature: Expire daemons in a smarter way

### Story: Stop Daemon after finishing execution if more than N daemons currently busy.

When a daemon finishes execution and there are > n daemons currently busy, then exit.

#### Implementation

- Define max number of busy daemons (can be referenced by internal system property ('org.gradle.daemon.expiration.maxcount', defaulting to __3__).
- When Daemon finishes build verify expiration strategy
    1. check number of busy daemons
    2. exit daemon process if busy daemons hits threshold.

##### Test Coverage

- Daemon process stops when finishing build and other daemons are busy
- threshold is configurable and respected by daemons.

### Story: Daemon quits when daemon registry not available

#### Implementation

- Introduce concept `DaemonExpirationStrategy` for Daemons to re-consider whether it should still be running.
- let idle daemon sleep for 60 seconds and then reevaluate its registered `DaemonExpirationStrategy`s.
- register `DaemonExpirationStrategy` to exit daemon if daemon registry can not be found.

##### Test Coverage

- all daemons stop when their registry is deleted.
- starting new build recreates registry.

### Story: least recently used daemon quits when more than N daemons available

#### Implementation

- register `DaemonExpirationStrategy` with strategy:
    - if more than N daemons available and I'm the least recently used, exit daemon.

##### Test Coverage

- starting > N daemons let least recently one exit within 60 seconds.
- 'org.gradle.daemon.expiration.maxcount' systemproperty is respected.

### Story: Expire least recently daemons exists if accumulated total heap size hits threshold

#### Implementation

- Define total max heap memory used by daemons (can be referenced by internal system property ('org.gradle.daemon.expiration.totalheapsize', defaulting to __4096m__).
- register `DaemonExpirationStrategy` with strategy:
    - if more than max defined heap size is used and I'm the least recently used, exit daemon.

##### Test Coverage

- starting daemon that causes the total heap size to exceed does stop least recently used daemon
- exceeding total heap size is possible for parallel running (`busy`) daemons.
- 'org.gradle.daemon.expiration.totalheapsize' systemproperty is respected.

### Story: Expire daemons from least recently used builds based on accumulated total percentage of available heap size

##### Implementation

##### Open questions

- How to gather these Statistics of current machine? (Add logic to native platform)

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
