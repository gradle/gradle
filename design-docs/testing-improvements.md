Improve TestNG test execution/reporting

# Use cases

1. Efficient generation of XML results
2. Efficient generation of HTML results
3. HTML results contain output per test VS output per test class
4. HTML results contain aggregated std err/std out VS separated

# Implementation plan

## Story: TestNG XML generation is efficient (DONE)

-test events/output stored in binary format (internal format geared towards efficiency)
    -2 files per test class (events and output, the latter is streamed), cache of open files
    -uses java serialization
    -the binary file with output is streamed. We should keep reasonable amount of files open
-parent process writes the binary results
-parent process writes the XML results

### Coverage

-update existing coverage if needed
-inconsistent data in the binary format: unfinished tests, output for tests with unknown id, etc
-no binary results after test execution
-file handles are closed

### Backwards compatibility

-story introduces negligible breaking change: if the worker process / parent process crashes, no XML results are generated at all. Previously, partial XML results are available.

## Story: TestNG produces the new XML/HTML result by default (DONE)

- Change the Test `testReport` default value to `true`.
- Change the Test `testNGOptions.useDefaultListeners` default value to `false`.
- Extract a shared `TestReporter` implementation out of `TestNGTestFramework` and `JUnitTestFramework` and remove `TestFramework.report()`.
- Update the documentation and release notes accordingly.

### Coverage

- Add a performance test with a build with many TestNG tests which do not generate any logging output. Verify that this build is not any slower than Gradle 1.0 or Gradle 1.3.
- Update `TestNGProducesJUnitXmlResultsIntegrationTest` so that it uses the default value for the `testReport` property.
- Update `TestNGProducesOldReportsIntegrationTest` so that it uses the appropriate values for the `testReport` property.
- Update performance tests to use the default setting for the `testReport` property.

### Backwards compatibility:

- Story changes the default values (for better, though)

## Story: JUnit XML generation is efficient

Use the same mechanism as TestNG for XML result generation

- Change `Test.executeTests()` so that it uses a TestReportDataCollector for all test frameworks (not just TestNG)
- Change `JUnitTestClassProcessor.startProcessing()` so that it no longer uses `JUnitXmlReportGenerator`.
- Remove `JUnitXmlReportGenerator`, `XmlTestSuiteWriter` and `XmlTestSuiteWriterFactory`.

### Coverage

- Add a performance test with a build with many JUnit tests which do not generate any logging output. Verify that this build is not any slower than Gradle 1.0 or Gradle 1.3.
- Add coverage for tests that don't have an associated method or class.
- Check that start-time and duration reported for a class should include all setup and tear-down.

### Backwards compatibility:

- No partial XML results available when process crashes (see first story for more)

## Story: HTML test report generation is efficient

HTML report is generated from the binary format, not from XML results

- Change `DefaultTestReport` to use `TestResultsProvider` to get results instead of loading from XML.
- Spike using [jatl](http://code.google.com/p/jatl/) to generate the report instead of using the DOM.
- Change the report rendering so that it copies the test output directly from `TestResultsProvider` to file, rather than loading it into heap.

## Story: Aggregate HTML test report can be generated

- Change test task to persist test results in internal format.
- Add test report task.

## Story: HTML test report shows output per test

-instead of showing output for the entire test class the report shows output per test method

## Story: HTML test report shows aggregated output (out + err)

-instead of showing separate tabs for out + err, there's a single tab 'output'

### Backwards compatibility:

-depending how the story is implemented, we might drop support for separate err and std output.
It's a breaking change in a way but I don't find the separate err/std output useful.

## Story: HTML report contains the output from TestNG Reporter

I'm hoping we won't have to implement it. I don't know how popular the Reporter is.
Also the problem can be avoided by using the Reporter methods that also print to the standard output (which is nice because the messages show up in IDE).

## Bug GRADLE-2524: Missing stuff in TestNG output

### Problem

There are a couple of jira tickets related to the problem with missing stuff in TestNG output.
Typically, those are outputs printed by TestNG listeners (our API enables hooking the listeners)
or outputs printed very early/very late in the game (BeforeSuite/AfterSuite, etc).

### Possible fix

TestNGTestClassProcessor uses CaptureTestOutputTestResultProcessor to capture the output.
However, the latter class is geared towards the JUnit model, where we receive notifications on class start / end.
In TestNG, we only receive notification on entire suite start / end and then on each test method start / end.
This means that with TestNG, the CaptureTestOutputTestResultProcessor is started only when the first test starts.
We could possibly fix it by starting redirecting the output at suite start in the TestNG scenario.

# Other issues

- Test report aggregates multiple test results with the same class name from separate test task executions.
- Allow XML results to be disabled.
- Provide some way to generate only the old TestNG reports, so that both test report and test XML generation can be disabled.
