
- Review and update performance tests to measure this and lock in improvements.
- Investigate file scanning done by the `Test` task. Initial observations have this task scanning its inputs many times. Investigate and fix.
- Change progress reporting to indicate when Gradle _starts_ running tests. Currently, progress is updated only on completion of the first test.
- Measure Gradle vs Maven and proceed further based on this.
    - Add performance tests to lock this in.
    - Profile and plan based on this
   