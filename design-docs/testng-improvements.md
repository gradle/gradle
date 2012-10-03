Improve TestNG test execution/reporting

# Use cases

1. GRADLE-2465. The current TestNG report does not contain the test output. This is not strictly a bug because in TestNG one should use Reporter API to report anything from tests.
  Similarly, if the SUT writes to standard output/error and one wants to see that in the test report, he should replace std out/err with an implementation that uses Reporter API behind the hood.
  I believe that the best user experience would be if the html reports from TestNG execution had the output included.
2. GRADLE-2465. The current TestNG report (especially when compared to the report after junit test execution) looks lame and is hard to navigate.
  We should look at using the same html report. Making both reports (junit/testNG) consistent also nicely maps to our vision of having common abstraction/API over different xunit frameworks.
3. GRADLE-2506, bug: as soon as a user starts using forkEvery or maxParallelForks, TestNG html report becomes garbage.


# Implementation plan

## Story 1: TestNG html report uses the same report as junit

### User visible changes

1. New html report is generated to the regular report location. It is somewhat a breaking change because a different report is generated.
    However, the new report has similar format (html) and offers much better readability and navigability.
2. Junit xml results are generated to the regular 'test results' location.
    Currently TestNG does generate the junit xml results but they don't contain output and we don't fully control the folder where they are generated.
3. To separate the new report (generated and owned by Gradle) from the old report (generated and owned by TestNG),
    the TestNG report will be generated "${testReportDir}-testNG" folder, whereas the Gradle report into regular testReportDir.
4. We should consider changing the default value of TestNGOptions.useDefaultListeners to false. There are 3 default listeners, html generator, xml junit results generator, xml generator.
    Since we're adding a new report, the html generator is no longer needed by default.
    Since we're adding proper generation of xml junit results (working with forkEvery/maxParallelForks, containing output), the TestNG xml junit results generator is no longer needed by default.
    The TestNG xml report (in a TestNG proprietary format) has some usages but I doubt it is very popular.
    Hence it makes sense not to use the default listeners by default.

### Backwards compatibility

Why is old TestNG reporting useful and what backwards compatibility should we consider:

1. Client CIs may be configured to look for reports in "${testReportDir}/junitreports".
    We could configure test results dir for TestNG to use "${testReportDir}/junitreports" dir instead of conventional ${testResultsDir}.
2. Clients may use the TestNG xml formatted results (testng-failed.xml and testng-results.xml) as input suites for next test execution.
    It is somewhat useful for the use case when one wants to rerun failed tests.
    However, any TestNG generated reports break if forkEvery/maxParallelForks are used.
3. We should probably avoid using TestNG to generate any kind of reports.
    If certain reports (like the suite xml with failed tests) are useful for our users we should make Gradle generate them.
4. TestNG html report contain the Reporter API logs. There's a different story for this.
5. TestNG html report includes a single-page, emailable html report.
    I'm not sure how useful it is these days, CIs tend to generate better emails anyway.
    I'm not sure how manageable the file is if one has lots of tests.

### Sad day cases

1. Some users might require the old reports. We need to make sure there's a way to get the old reports.

### Test coverage

1. new xml results are generated to the test results dir
2. new html results are generated to the test report dir
3. xml/html results contain the test output
4. works well with forkEvery/maxParallelForks
5. does not break when there are no tests
6. reports failing and passing tests

### Implementation approach

1. Refactor the existing JUnitXmlReportGenerator so that it can be used by TestNGTestClassProcessor and JUnitTestNGTestClassProcessor
    Currently it is tailored to the JUnit execution and requires certain events that we only have when we run JUnit tests.
2. Both JUnit and TestNG must use the same processors.

## Story 2: Html report contains the output from TestNG Reporter.

I think this story has low priority because I don't know how popular the Reporter is.
Also the problem can be worked around by using the Reporter methods that also print to the standard output.
Using those methods seems to be a good idea in any case because it makes the reporter output visible when tests are executed in the IDE which is invaluable.

## Story 3: Generate test results in a Gradle format

This way can resolve some limitations of the current report.
For example: we could provide merged standard output and error instead of showing 2 separate streams;
    we could also show output per test method instead of showing output per test class.

We have to continue generating standard JUnit xml results because they are de facto a standard in the CI servers and other tools.

# Open issues

This stuff is never done. This section is to keep track of assumptions and things we haven't figured out yet.
