Improve TestNG test execution/reporting

# Use cases

1. Efficient generation of xml results
2. Efficient generation of html results
3. html results contain output per test VS output per test class
4. html results contain aggregated std err/std out VS separated

# Implementation plan

## Story: TestNG xml generation is efficient

-test events/output stored in binary format (internal format geared towards efficiency)
    -2 files per test class (events and output, the latter is streamed), cache of open files
    -uses java serialization
    -the binary file with output is streamed. We should keep reasonable amount of files open
-parent process writes the binary results
-parent process writes the xml results

### Coverage

-update existing coverage if needed
-inconsistent data in the binary format: unfinished tests, output for tests with unknown id, etc
-no binary results after test execution
-file handles are closed

### Backwards compatibility

-story introduces negligible breaking change: if the worker process / parent process crashes, no xml results are generated at all. Previously, partial xml results are available.

## Story: TestNG produces the new xml/html result by default

-change the testReport default value to 'true'
-change the testNGOptions.useDefaultListeners default value to 'false'
-there is a way to *only* generate the old reports
    -generation of new xml results is configurable (atm, it isn't)
-update the documentation and release notes accordingly

### Coverage

-update existing coverage
-tweak performance tests

### Backwards compatibility:

-story changes the default values (for better, though)

## Story: JUnit xml generation is efficient

-use the same mechanism as TestNG for xml generation
-remove the existing JUnit classes that are no longer needed

### Coverage

-tweak performance tests
-tests that don't have an associated method or class

### Backwards compatibility:

-no partial xml results available when process crashes (see first story for more)

## Story: html test report generation is efficient

-html report is generated from the binary format, not from xml results

## Story: html test report shows output per test

-instead of showing output for the entire test class the report shows output per test method

## Story: html test report shows aggregated output (out + err)

-instead of showing separate tabs for out + err, there's a single tab 'output'

### Backwards compatibility:

-depending how the story is implemented, we might drop support for separate err and std output.
It's a breaking change in a way but I don't find the separate err/std output useful.

## Story: html report contains the output from TestNG Reporter.

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

Check if