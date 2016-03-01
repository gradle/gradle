## Goal

The performance improvements rely on automated metrics gathering to track progress and avoid regressions. 
The performance test suite and related infrastructure is used for this.
This feature outlines a small set of changes to this infrastructure to improve efficiency of Gradle developers who are using or improving the performance tests are part of working on performance improvements.

## Potential changes

- Performance experiment test build runs on any linux agent. This means that the performance experiment builds will not be blocked waiting
  for the long running performance tests to complete.
    - Need some way to prevent other builds running on the same build vm
- Not all performance experiment tests run as part of performance test suite. This allows experiments to be used while working on a particular improvements.
  It also allows performance tests to be developed without breaking the performance test suite.
- Allow ad hoc performance (experiment) test to be run on any agent, including Windows agents. This allows results to be gathered without blocking the developer's 
  machine.

## Backlog

These are currently out of scope:

- Shorter performance test feedback
    - Move the test results to a db-as-a-service somewhere (Heroku, for example)
    - Split the performance tests into several chunks 
    - Allow performance tests to run on an Linux VM, ensure no other builds run on same machine
    - Collect and report on host where performance test runs
    