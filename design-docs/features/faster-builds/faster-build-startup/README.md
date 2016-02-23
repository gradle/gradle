Investigate and improve build configuration time, when build configuration inputs such as build scripts or Gradle or Java version have not changed.

Audience is developers that are using the Gradle daemon.

### Implementation plan

- Review and update performance tests to measure this and lock in improvements.
- Profile test builds and use results to select improvements to implement.
- Profile daemon client

### Potential improvements 

- Fix hotspots identified by profiling
- Send messages to daemon client asynchronously
- Faster rule execution for task configuration
- Reuse build script cache instances across builds
- Don't hash the build script contents on each build
- Make creation of project instances cheaper
- Faster startup by reducing fixed costs in daemon client and per build setup 
- Start progress logging earlier in build lifecycle to give more insight into what's happening early in the build 
