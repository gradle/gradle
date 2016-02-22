Changes to improve build configuration time, when build configuration inputs have not changed.

Audience is developers that are using the Gradle daemon.

- Review and update performance tests to measure this and lock in improvements.
- Profile to validate the following plan

### Potential improvements 

- Fix hotspots identified by profiling
- Send messages to daemon client asynchronously
- Faster rule execution for task configuration
- Reuse build script cache instances across builds
- Don't hash the build script contents on each build
- Make creation of project instances cheaper
