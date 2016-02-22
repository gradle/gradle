### Implementation plan

- Review and update performance tests to measure this and lock in improvements.
- Measure Gradle vs Maven and proceed further based on this.
    - Add performance tests to lock this in.
- Profile test builds and use results to select improvements to implement 

### Potential improvements   

- Fix hotspots identified by profiling
- Investigate file scanning done by the `Test` task. Initial observations have this task scanning its inputs many times. Investigate and fix.
- Change progress reporting to indicate when Gradle _starts_ running tests. Currently, progress is updated only on completion of the first test.
  This doesn't make test execution faster, makes for a fairer subjective comparison between Gradle and Maven. 
