
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

