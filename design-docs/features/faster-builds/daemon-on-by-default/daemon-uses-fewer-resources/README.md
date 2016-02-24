Daemon uses fewer resources on developer machine.

## Implementation plan

- Expire daemons in a smarter way
- Reduce wasted heap in the daemon  
- Reduce default heap sizes for the daemon

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
