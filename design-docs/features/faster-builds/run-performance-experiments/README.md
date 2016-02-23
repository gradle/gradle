Changes to improve efficiency of Gradle developers who are using or improving the performance test infrastructure, while working on performance improvements.

## Potential changes

- Performance experiment test build runs on any linux agent. This means that the performance experiment builds will not be blocked waiting
  for the long running performance tests to complete.
- Not all performance experiment tests run as part of performance test suite. This allows experiments to be used while working on a particular improvements.
  It also allows performance tests to be developed without breaking the performance test suite.
- Allow ad hoc performance (experiment) test to be run on any agent, including Windows agents. This allows results to be gathered without blocking the developer's 
  machine.
