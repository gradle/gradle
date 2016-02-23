Daemon uses fewer resources on developer machine.

## Candidate stories

### Story - Prefer a single daemon instance

Improve daemon expiration algorithm so that when there are multiple daemon instances running, one instance is
selected as the survivor and the others expire quickly (say, as soon as they become idle).

See (GRADLE-1890)[https://issues.gradle.org/browse/GRADLE-1890]

### Story - Cross-version daemon management

Daemon management, such as `gradle --stop` and the daemon expiration algorithm should consider daemons across all Gradle versions.

See [GRADLE-1891](https://issues.gradle.org/browse/GRADLE-1891)

### Story - Reduce the default daemon maximum heap and permgen sizes

Should be done in a backwards compatible way.

## Clean up build ClassLoaders

Also close the associated caches.
