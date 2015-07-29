Improve test execution and reporting

# Use cases

1. Efficient generation of XML results
2. Efficient generation of HTML results
3. HTML results contain output per test VS output per test class
4. HTML results contain aggregated std err/std out VS separated

# Implementation plan

## Story: Add a JaCoCo code coverage plugin

- Merge the [JaCoCo plugin pull request](https://github.com/gradle/gradle/pull/138)
- Add `@Incubating` to public task, plugin and extension types.
- Move tasks to `org.gradle.testing.jacoco.tasks`
- Move plugin and extensions to `org.gradle.testing.jacoco.plugin`
- Move default destination dir from `JacocoMerge` to plugin.
- Move default report dir from `JacocoReport` to plugin.
- Fix up input and output files on `JacocoReport`.
- Change the reporting task so that it `mustRunAfter` the test task rather than depends on the test task
- Running `gradle check` should generate the unit test report
- Flip the relationship between `jacoco` plugin and `sonar` plugins.
- Flip the relationship between `jacoco` plugin and `java` plugins.
- Add `jacoco.reportFormat` extension property.
- `JacocoReport` should not use sourcesets to represent source
- Rename the `jacoco` project to `testing`.
- Move the testing infrastructure into this project.
- Change the reporting task to implement `Reporting`.
- Merge `JacocoReport.sourceDirectories` and `additionalSourceDirectories`.
- Merge `JacocoReport.classDirectories` and `additionalClassDirectories`.
- Move the input execution data stuff up to `JacocoBase`.
- Separate coverage infrastructure and convention.
- Fail build if certain coverage thresholds have not been reached.
- Document the defaults for `JaCoCoTaskExtension`.
- Introduce the concept of a test suite.
    - Add a container of test suites.
    - Each test suite has an associated test task.
    - Each test suite has an associated test binary and production binary.
    - The java plugin (or whatever) defines a single test suite.
    - The jacoco plugin should add a report task for each test suite for which coverage is enabled.
    - The jacoco plugin should use the production binary to decide which classes and source files to
      report on.

### Test coverage

- Report is generated when all tests fail and run with `--continue`.
- Report is generated when one or more tests fail and run with `--continue`.
- Report is not generated when tests cannot be run.
- Dashboard report includes link to each coverage report.
- Running `gradle jacocoTestReport` generates empty report if test has not been run.
- Running `gradle test jacocoTestReport` generates report.

## Story: `Test` task implements the `Reporting` contract

### Implementation plan

Move reporting configuration into this framework, leaving existing Test properties/methods in place as deprecated facades.

### User visible changes

The Test task reports container will expose the HTML and XML reports…

    test {
      reports {
        html.enabled = false
        xml.enabled = false
      }
    }

The standard `Report` options will be respected. This deprecates Test methods/properties such as `testReportEnabled`, `testReportDir`.

### Test coverage

- HTML and XML reports can be disabled
- Changing HTML report configuration through old properties (e.g. `testReportDisabled`) is respected (and deprecated)

## Story: XML test report shows output per test

This is about providing a way to produce XML like:

    <?xml version="1.1" encoding="UTF-8"?>
    <testsuite name="junit.TheTest" tests="2" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="hasdrubal.local" time="1.366372749405E9">
     <properties/>
     <testcase name="t1" classname="junit.TheTest" time="0.001">
       <system-out><![CDATA[from t1]]></system-out>
     </testcase>
     <testcase name="t2" classname="junit.TheTest" time="0.001">
       <system-out><![CDATA[from t2]]></system-out>
     </testcase>
    </testsuite>

Where as we currently produce:

    <?xml version="1.1" encoding="UTF-8"?>
    <testsuite name="junit.TheTest" tests="1" failures="0" errors="0" timestamp="1970-01-01T00:00:00" hostname="hasdrubal.local" time="1.366372749405E9">
     <properties/>
     <testcase name="t" classname="junit.TheTest" time="0.001"/>
     <testcase name="t2" classname="junit.TheTest" time="0.001/">
     <system-out><![CDATA[from t1
    from t2]]></system-out>
    </testsuite>

Jenkins support the output-nested-under-testcase approach outlined above. Having output in this format will produce better test result reporting in the Jenkins UI.

As this is a communication protocol between Gradle and other tools (e.g. CI servers) changing this format needs to be managed carefully.

### User visible changes

A boolean property named '`outputPerTestCase`' will be added to the `Report` that represents the XML file report.

To enable this feature:

    test {
      reports {
        junitXml.outputPerTestCase true
      }
    }

### Implementation

Our test execution infrastructure already associates output events with test cases. When we serialise the results to disk back in the build process we discard this association and write a single text file for each stream (out & err) of each class. The JUnit XML generator uses these files (along with the binary results file) to generate the XML.

Instead of writing the output to text files, we will serialize the TestOutputEvent into the results binary file.

The data structure that gets serialized must provide the means to reconstruct the ordered "stream" of output events:

1. for the execution of each suite
2. for the execution of each test case within a suite

This implies that a sequence identifier id is added to the association between an output event and the test suite, and the output event and the test case. There is no requirement at this time to be able to reconstruct the ordered stream of output events for an execution (i.e. all tests).

The result data structure needs to support being able to render the HTML and XML reports efficiently. Different ways of structuring the output event storage (e.g. are event children of `TestMethodResult` or `TestClassResult`?) will need to be trialled and measured.

The impact of the new data structure on report generation will need to be measured for both the output-per-suite case and the output-per-test-case case.

### Test coverage

- Test class with multiple methods producing output produces correctly nested output elements in XML (JUnit and TestNG)
- Test class with parameterised methods producing output produces correctly … (JUnit and TestNG)
- Useful output/result when test case names are not unique
- With `outputPerTestCase` on, output from class level methods (e.g. `@BeforeClass`) is associated with the test class
- With `outputPerTestCase` on, output from test case level methods (e.g. `@Before`) is associated with the test case
- With `outputPerTestCase` off, existing output format is unchanged (should be covered by not introducing failures for our existing tests)
- Output is correctly associated with test cases when a test cases logs output from multiple threads

## Story: A user uses a test execution mode that executes test cases concurrently within the same VM

Our test listening/event infrastructure does not support this in so far that it does not correctly associate output from test cases correctly when firing test output events.

We also do not have a suitable level of test coverage of this kind of test parallelism.

Potential execution modes:

1. JUnit Suite using org.junit.experimental.ParallelComputer (classes and method level computers)
2. TestNG managed parallelism (class and method level)

### Implementation plan

`CaptureTestOutputTestResultProcessor` (and associated classes) will need to handle concurrent output events.
As output events are generated by tests writing to standard streams, a facade stream will need to be inserted that uses thread local tracking internally to associate output with test cases.
As tests may spawn their own threads, an inheritable thread local strategy will need to be used.

### User visible changes

Test output is associated with the correct test when test cases are run concurrently in the same VM.

### Test coverage

- When using intra JVM parallelism (types listed above), test output listeners receive test output events associated with the correct test case
  - Output during test cases
  - Output during preflight/postflight methods (e.g. @Before, @BeforeClass etc.)
- Test cases spawn threads that produce output
- When generating JUnit XML report with output per test case, output is correctly associated to test cases

## Story: HTML test report shows number of ignored tests

## Story: HTML test report shows output per test

- Need to fix `AggregateTestResultsProvider` as it does not correctly associate a given test execution with the correct provider.

### Test cases

- Test task executes a test suite that executes the same class and method multiple times, generating different output each time. Verify that HTML and Junit XML associate
the correct output with each test execution.
- Multiple test tasks execute the same test suite, generating different output each time. Verify that the `TestReportTask` generates an HTML that associates the correct
output with each test execution.

## Story: HTML test report shows aggregated output (out + err)

Introduce a timeline of the test execution that visualizes the output and error logging of the tests for a class:

- Instead of showing separate tabs for out + err, there's a single tab 'output'
- stdout and stderr are rendered in visually distinct ways
- Interleave the test start and complete events into the output.

## Story: HTML test report shows runtime grouping of tests into suites

- Show the runtime tree of test execution to the test report, in addition to the current implementation view.
- Include some way to attach context to a test suite to include in the reports

## Story: Aggregate HTML test report shows execution of same class in multiple suites

Currently the aggregate test report implementation cannot handle a given class executing in multiple suites.

## Story: HTML report contains the output from TestNG Reporter

I'm hoping we won't have to implement it. I don't know how popular the Reporter is.
Also the problem can be avoided by using the Reporter methods that also print to the standard output (which is nice because the messages show up in IDE).

## Separate test report generation from test execution

Configure a `TestReport` task to always run after the `Test` task, and change the `TestReport` type to implement `Reporting`. Remove (via deprecation) the reporting from the `Test` task type.

## Improve Test filtering capabalities

### Problems

* When a test class' test runner does not implement Filterable, gradle runs all the tests from that class, regardless of whether they were requested or not.
* When a test's name does not exactly match the method name, gradle cannot run the test. Think Spock @Unroll, JUnits @RunWith(Parameterized.class) and similar.
* When a test's suite name does not exactly match the class name, gradle doesn't show the tests correctly in the UI and we cannot rerun them.
* Gradle generates empty test suites for all test classes that don't match, and generated test descriptors for them.
* The 'could not find any matches' validation is not robust wrt things like #1 and so in this instance when you make a typo in your test request, we run exactly the wrong tests _and_ don't tell you about it.

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

- Provide some way to generate only the old TestNG reports, so that both test report and test XML generation can be disabled.
- Reports should distinguish between tests and configure operations.
