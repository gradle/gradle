/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.testing

import com.google.common.collect.ImmutableListMultimap
import org.gradle.api.internal.tasks.testing.report.AggregateTestReportFixture
import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.internal.GUtil

import static org.gradle.util.Matchers.containsText
import static org.hamcrest.CoreMatchers.equalTo

class TestEventReporterHtmlReportIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    def "successful tests do not emit HTML reports to console"() {
        given:
        buildFile << passingTask("passing")

        when:
        succeeds("passing")

        then:
        outputDoesNotContain("See the test results for more details")
        outputDoesNotContain("Aggregate test results")

        // Aggregate report is still emitted even if we don't print the URL to console
        def aggregateReport = AggregateTestReportFixture.of(testDirectory)
        aggregateReport.rows == [new AggregateTestReportFixture.Row("passing", "../tests/passing/index.html", "passed", 1, 1, 0, 0)]
        aggregateReport.linkedReport("passing") == file("build/reports/tests/passing/index.html")
    }

    def "HTML report contains output at task level only"() {
        given:
        buildFile << passingTask("passing", true)

        when:
        succeeds("passing")

        then:
        def results = resultsFor("tests/passing")
        results
            .testPath(":passing suite")
            .onlyRoot()
            .assertStdout(equalTo(""))
            .assertStderr(equalTo(""))
        results
            .testPath(":passing suite:passing test")
            .onlyRoot()
            .assertStdout(equalTo("standard out text"))
            .assertStderr(equalTo("standard error text"))
    }

    def "HTML report contains failure message"() {
        given:
        buildFile << failingTask("failing")

        when:
        fails("failing")

        then:
        def results = resultsFor("tests/failing")
        results
            .testPath(":failing suite:failing test")
            .onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsText("failure message"))
    }

    def "HTML report contains metadata from both suites"() {
        given:
        buildFile """
            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        name,
                        getLayout().getBuildDirectory().dir("test-results/" + name).get(),
                        getLayout().getBuildDirectory().dir("reports/tests/" + name).get()
                    )) {
                       reporter.started(Instant.now())
                       for (int i = 0; i < 2; i++) {
                           try (def mySuite = reporter.reportTestGroup(name + " suite")) {
                                mySuite.started(Instant.now())
                                try (def myTest = mySuite.reportTest(name + " test", name + " test")) {
                                     myTest.started(Instant.now())
                                     myTest.succeeded(Instant.now())
                                }
                                mySuite.metadata(Instant.now(), "index", ""+i)
                                mySuite.succeeded(Instant.now())
                           }
                       }
                       reporter.failed(Instant.now())
                   }
                }
            }
            tasks.register("doubled", CustomTestTask)
        """

        when:
        fails("doubled")

        then:
        def results = resultsFor("tests/doubled")
        results
            .testPath(":doubled suite")
            .onlyRoot()
            .assertOnlyChildrenExecuted("doubled test", "doubled test")
            .assertMetadata(ImmutableListMultimap.of(
                "index", "0",
                "index", "1"
            ).entries().toList())
    }

    def "HTML report is correct when test group is re-emitted without children"() {
        given:
        buildFile reemitsGroupWithoutChildren("succeeded(Instant.now())")

        when:
        succeeds("reemitted")

        then:
        def results = resultsFor("tests/reemitted")
        results
            .testPath(":reemitted suite")
            .onlyRoot()
            .assertChildCount(1, 0)
            .assertSummaryCounts(1, 0, 0)
        results
            .testPath(":")
            .onlyRoot()
            .assertSummaryCounts(1, 0, 0)
    }

    def "HTML report shows failure of test group re-emitted without children as an execution error"() {
        given:
        buildFile reemitsGroupWithoutChildren("failed(Instant.now(), \"lost worker\")", "failed(Instant.now())")

        when:
        fails("reemitted")

        then:
        def results = resultsFor("tests/reemitted")
        results
            .testPath(":reemitted suite")
            .onlyRoot()
            .assertOnlyChildrenExecuted("reemitted test", "executionError")
            .assertChildrenFailed("executionError")
            .assertSummaryCounts(2, 1, 0)
            .assertChildrenInTab("Failed", "executionError")
        results
            .testPath(":reemitted suite:executionError")
            .onlyRoot()
            .assertHasResult(TestResult.ResultType.FAILURE)
            .assertFailureMessages(containsText("lost worker"))
        results
            .testPath(":")
            .onlyRoot()
            .assertSummaryCounts(2, 1, 0)
            .assertChildrenInTab("Failed", "reemitted suite")
    }

    def "HTML report does not count test group skipped when re-emitted without children"() {
        given:
        buildFile reemitsGroupWithoutChildren("skipped(Instant.now())")

        when:
        succeeds("reemitted")

        then:
        def results = resultsFor("tests/reemitted")
        results
            .testPath(":reemitted suite")
            .onlyRoot()
            .assertChildCount(1, 0)
            .assertSummaryCounts(1, 0, 0)
        results
            .testPath(":")
            .onlyRoot()
            .assertSummaryCounts(1, 0, 0)
            .assertChildrenInTab("Skipped")
    }

    def "HTML report shows test group that fails with only passing children as failed"() {
        given:
        buildFile """
            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        name,
                        getLayout().getBuildDirectory().dir("test-results/" + name).get(),
                        getLayout().getBuildDirectory().dir("reports/tests/" + name).get()
                    )) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup(name + " suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest(name + " test", name + " test")) {
                                 myTest.started(Instant.now())
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.failed(Instant.now(), "teardown failed")
                       }
                       reporter.failed(Instant.now())
                   }
                }
            }
            tasks.register("failing", CustomTestTask)
        """

        when:
        fails("failing")

        then:
        def results = resultsFor("tests/failing")
        results
            .testPath(":failing suite")
            .onlyRoot()
            .assertSummaryCounts(1, 0, 0)
        results
            .testPath(":")
            .onlyRoot()
            .assertSummaryCounts(1, 0, 0)
            .assertChildrenInTab("Failed", "failing suite")
    }

    /**
     * A task that reports a test group with a single passing test, then reports the same group again without any children.
     */
    private static String reemitsGroupWithoutChildren(String secondRunOutcome, String rootOutcome = "succeeded(Instant.now())") {
        return """
            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        name,
                        getLayout().getBuildDirectory().dir("test-results/" + name).get(),
                        getLayout().getBuildDirectory().dir("reports/tests/" + name).get()
                    )) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup(name + " suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest(name + " test", name + " test")) {
                                 myTest.started(Instant.now())
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                       }
                       try (def mySuite = reporter.reportTestGroup(name + " suite")) {
                            mySuite.started(Instant.now())
                            mySuite.${secondRunOutcome}
                       }
                       reporter.${rootOutcome}
                   }
                }
            }
            tasks.register("reemitted", CustomTestTask)
        """
    }

    def "emits test results in error exception message when test fails"() {
        given:
        buildFile << failingTask("failing")

        when:
        fails("failing")

        then:
        failure.assertHasCause("Test(s) failed.")
        failure.assertHasErrorOutput("See the report at: " + resultsUrlFor("failing"))
        resultsFor("tests/failing")
            .testPath(":failing suite")
            .onlyRoot()
            .assertChildCount(1, 1)

        // Aggregate results are still emitted even if we don't print the URL to console
        outputDoesNotContain("Aggregate test results")
        AggregateTestReportFixture.of(testDirectory).rows == [new AggregateTestReportFixture.Row("failing", "../tests/failing/index.html", "failed", 1, 0, 1, 0)]
    }

    def "does not print aggregate test results URL if only one test task fails"() {
        given:
        buildFile << passingTask("passing")
        buildFile << failingTask("failing")

        when:
        fails("passing", "failing", "--continue")

        then:
        failure.assertHasErrorOutput("See the report at: " + resultsUrlFor("failing"))

        // Aggregate results are still emitted even if we don't print the URL to console
        outputDoesNotContain("Aggregate test results")
        def aggregateReport = AggregateTestReportFixture.of(testDirectory)
        aggregateReport.totals == [tests: "2", failures: "1", skipped: "0", successful: "50%"]
        aggregateReport.rows*.name() == ["failing", "passing"]
        aggregateReport.failedRows*.name() == ["failing"]
    }

    def "prints aggregate test results URL if multiple test tasks fail"() {
        given:
        buildFile << failingTask("failing1")
        buildFile << failingTask("failing2")

        when:
        fails("failing1", "failing2", "--continue")

        then:
        failure.assertHasDescription("Execution failed for task ':failing1'.")
        failure.assertHasErrorOutput("See the report at: " + resultsUrlFor("failing1"))
        resultsFor("tests/failing1")
            .testPath(":failing1 suite")
            .onlyRoot()
            .assertChildCount(1, 1)

        failure.assertHasDescription("Execution failed for task ':failing2'.")
        failure.assertHasErrorOutput("See the report at: " + resultsUrlFor("failing2"))
        resultsFor("tests/failing2")
            .testPath(":failing2 suite")
            .onlyRoot()
            .assertChildCount(1, 1)

        def aggregateReport = AggregateTestReportFixture.of(testDirectory)
        outputContains("Aggregate test results: " + new ConsoleRenderer().asClickableFileUrl(aggregateReport.indexFile))
        aggregateReport.rows*.name() == ["failing1", "failing2"]
        aggregateReport.linkedReport("failing1") == file("build/reports/tests/failing1/index.html")
        aggregateReport.linkedReport("failing2") == file("build/reports/tests/failing2/index.html")
    }

    def "aggregate report shows test executions that failed without failed tests as failed"() {
        given:
        buildFile << failingWithoutFailedTestsTask("broken1")
        buildFile << failingWithoutFailedTestsTask("broken2")

        when:
        fails("broken1", "broken2", "--continue")

        then:
        failure.assertHasFailures(2)

        def aggregateReport = AggregateTestReportFixture.of(testDirectory)
        outputContains("Aggregate test results: " + new ConsoleRenderer().asClickableFileUrl(aggregateReport.indexFile))
        aggregateReport.rows == [
            new AggregateTestReportFixture.Row("broken1", "../tests/broken1/index.html", "failed", 1, 1, 0, 0),
            new AggregateTestReportFixture.Row("broken2", "../tests/broken2/index.html", "failed", 1, 1, 0, 0)
        ]
        aggregateReport.failedRows*.name() == ["broken1", "broken2"]
    }

    def "stale aggregate reports are deleted if no tests were executed"() {
        given:
        buildFile << failingTask("failing")

        when:
        fails("failing")

        then:
        def aggregateReportFile = file("build/reports/aggregate-test-results/index.html")
        aggregateReportFile.assertExists()

        // Run again without any tests
        when:
        succeeds("help")

        then:
        aggregateReportFile.assertDoesNotExist()
    }

    def "aggregate report lists test executions by name"() {
        given:
        buildFile << passingTask("aFirst")
        buildFile << failingTask("bSecond")
        buildFile << failingTask("cThird")
        buildFile << passingTask("dFourth")

        when:
        fails("--continue", "aFirst", "bSecond", "cThird", "dFourth")

        then:
        failure.assertHasFailures(2)

        AggregateTestReportFixture.of(testDirectory).rows*.name() == ["aFirst", "bSecond", "cThird", "dFourth"]
        AggregateTestReportFixture.of(testDirectory).failedRows*.name() == ["bSecond", "cThird"]
    }

    def "aggregate report disambiguates test executions with the same name and links to each of their reports"() {
        given:
        buildFile <<
            """
            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @Input
                abstract Property<String> getChangingString()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "TheSameName",
                        getLayout().getBuildDirectory().dir("test-results/\${getChangingString().get()}").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/\${getChangingString().get()}").get()
                    )) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("TheSameName suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("\${getChangingString().get()} test", "\${getChangingString().get()} test")) {
                                 myTest.started(Instant.now())
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                       }
                       reporter.succeeded(Instant.now())
                   }
                }
            }

            tasks.register("theSameName1", CustomTestTask) {
                changingString = "theSameName1"
            }
            tasks.register("theSameName2", CustomTestTask) {
                changingString = "theSameName2"
            }
            """

        when:
        succeeds("theSameName1", "theSameName2")


        then:
        AggregateTestReportFixture.of(testDirectory).rows*.name() == ["TheSameName (1)", "TheSameName (2)"]
        AggregateTestReportFixture.of(testDirectory).rows*.href() == ["../tests/theSameName1/index.html", "../tests/theSameName2/index.html"]
    }

    def "child results are sorted lexicographically ignoring case"() {
        given:
        def identifyingString = "the test is: "
        def theResults = [
            "b", "z", "[", "4", "*", "!", "Θ", "Δ", "a", ":", "foo", "Foo", "bar", "Bar"
        ]

        def resultProducers = ""
        for (def result : theResults) {
            resultProducers += """
                try (def myTest = reporter.reportTest("${identifyingString}${result}", "${identifyingString}${result}")) {
                     myTest.started(Instant.now())
                     Thread.sleep(10)
                     myTest.succeeded(Instant.now())
                }
            """
        }

        buildFile <<
            """
            abstract class CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "MyTest",
                        getLayout().getBuildDirectory().dir("test-results/\${name}").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/\${name}").get()
                    )) {
                        reporter.started(Instant.now())
                        ${resultProducers}
                        reporter.succeeded(Instant.now())
                    }
                }
            }

            tasks.register("test", CustomTestTask)
            """

        when:
        succeeds("test")


        then:
        def tests = resultsFor(testDirectory)
        def testNames = theResults.collect { "the test is: " + it }.toSorted(GUtil.caseInsensitive())
        tests.testPath(":").onlyRoot().assertOnlyChildrenExecuted(*testNames)
    }

    def "HTML report renders sortable markup for test-results tables"() {
        given:
        buildFile << passingTask("passing")

        when:
        succeeds("passing")

        then:
        def results = resultsFor("tests/passing")
        // Table is marked sortable and its data rows live inside a <tbody>
        results.assertHtml("table.test-results.sortable > tbody > tr") {
            assert !it.isEmpty()
        }
        // Numeric columns default to descending on first click
        results.assertHtml("table.test-results th[data-sort-default=desc]") {
            def headers = it*.text()
            assert headers.containsAll(["Tests", "Failures", "Skipped", "Duration"])
            assert !headers.contains("Success rate")
            assert !headers.contains("Child")
        }
        // Duration cells carry a numeric data-sort-value (parseable as a long)
        results.assertHtml("table.test-results > tbody > tr > td[data-sort-value]") {
            assert !it.isEmpty()
            it.each { cell ->
                Long.parseLong(cell.attr("data-sort-value"))
            }
        }
    }

    def "HTML report contains custom entries from contributors"() {
        given:
        buildFile << contributorTask("""
            class CustomContributor implements TestReportEntryContributor {
                String getEntryName() { "Custom" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.html { htmlOut ->
                        htmlOut.append("<p class='name'>").append(context.testDescriptor.displayName).append("</p>")
                        htmlOut.append("<p class='composite'>").append(String.valueOf(context.testDescriptor.composite)).append("</p>")
                        htmlOut.append("<p class='result'>").append(context.resultType.name()).append("</p>")
                        htmlOut.append("<p class='unclosed'>unclosed")
                    }
                }
            }
        """, "new CustomContributor()")

        when:
        fails("custom")

        then:
        def results = resultsFor("tests/custom")

        results.getAllTabNames("suite/withOutput.html").contains("Custom")
        results.assertHtml("suite/withOutput.html", ".custom-entry > template[shadowrootmode=open] > p.name") {
            assert it*.text() == ["withOutput"]
        }
        results.assertHtml("suite/withOutput.html", ".custom-entry > template > p.composite") {
            assert it*.text() == ["false"]
        }
        results.assertHtml("suite/withOutput.html", ".custom-entry > template > p.result") {
            assert it*.text() == ["SUCCESS"]
        }
        // The unclosed element is closed by the end of the shadow root template, so it shouldn't affect the rest of the page
        results.assertHtml("suite/withOutput.html", ".custom-entry > template > p.unclosed") {
            assert it*.text() == ["unclosed"]
        }
        results.assertHtml("suite/withOutput.html", "pre") {
            assert it.every { pre -> pre.parents().every { !it.hasClass("unclosed") } }
        }

        results.assertHtml("suite/failing.html", ".custom-entry > template > p.result") {
            assert it*.text() == ["FAILURE"]
        }

        // Containers get entries too
        results.assertHtml("suite/index.html", ".custom-entry > template > p.name") {
            assert it*.text() == ["suite"]
        }
        results.assertHtml("suite/index.html", ".custom-entry > template > p.composite") {
            assert it*.text() == ["true"]
        }
    }

    def "HTML report contains entry when contributor writes blank HTML"() {
        given:
        buildFile << contributorTask("""
            class EmptyContributor implements TestReportEntryContributor {
                String getEntryName() { "Empty" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.html { htmlOut -> }
                }
            }
        """, "new EmptyContributor()")

        when:
        fails("custom")

        then:
        def results = resultsFor("tests/custom")
        results.getAllTabNames("suite/withOutput.html").contains("Empty")
        results.assertHtml("suite/withOutput.html", ".custom-entry > template") {
            assert it.every { it.childNodes().isEmpty() }
        }
    }

    def "HTML report does not contain entry when contributor returns empty content"() {
        given:
        buildFile << contributorTask("""
            class LeafOnlyContributor implements TestReportEntryContributor {
                String getEntryName() { "Leaf only" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    if (context.testDescriptor.composite) {
                        return TestReportEntryContent.empty()
                    }
                    TestReportEntryContent.html { htmlOut -> htmlOut.append("<p class='leaf'>leaf</p>") }
                }
            }
        """, "new LeafOnlyContributor()")

        when:
        fails("custom")

        then:
        def results = resultsFor("tests/custom")
        !results.getAllTabNames("suite/index.html").contains("Leaf only")
        results.getAllTabNames("suite/withOutput.html").contains("Leaf only")
        results.assertHtml("suite/withOutput.html", ".custom-entry > template > p.leaf") {
            assert it*.text() == ["leaf"]
        }
    }

    def "HTML report contains large custom entries with text and comments between elements"() {
        given:
        buildFile << contributorTask("""
            class LargeContributor implements TestReportEntryContributor {
                String getEntryName() { "Large" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.html { htmlOut ->
                        htmlOut.append("before")
                        for (int i = 0; i < 10000; i++) {
                            htmlOut.append("<!-- comment --><p class='line'>line ").append(String.valueOf(i)).append("</p>")
                            htmlOut.append('|')
                        }
                        htmlOut.append("after")
                    }
                }
            }
        """, "new LargeContributor()")

        when:
        fails("custom")

        then:
        def results = resultsFor("tests/custom")
        results.assertHtml("suite/withOutput.html", ".custom-entry > template > p.line") {
            assert it.size() == 10000
            assert it.first().text() == "line 0"
            assert it.last().text() == "line 9999"
        }
        results.assertHtml("suite/withOutput.html", ".custom-entry > template") {
            def entry = it.first()
            assert entry.ownText() == "before" + ("|" * 10000) + "after"
            assert entry.html().contains("<!-- comment -->")
        }
    }

    def "build fails when contributor fails #scenario"() {
        given:
        buildFile << contributorTask("""
            class BoomContributor implements TestReportEntryContributor {
                String getEntryName() { "Boom" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    ${contentFor}
                }
            }
        """, "new BoomContributor()")

        when:
        fails("custom")

        then:
        failure.assertHasCause("Could not render custom test report entry 'Boom' with BoomContributor.")
        failure.assertHasCause("boom!")

        where:
        scenario                  | contentFor
        "creating content"        | 'throw new RuntimeException("boom!")'
        "writing HTML of content" | 'TestReportEntryContent.html { htmlOut -> htmlOut.append("<p>partial"); throw new RuntimeException("boom!") }'
    }

    def "build fails when contributor writes #tag"() {
        given:
        buildFile << contributorTask("""
            class TemplateContributor implements TestReportEntryContributor {
                String getEntryName() { "Template" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.html { htmlOut ->
                        ${appends.collect { "htmlOut.append('${it}')" }.join("\n")}
                    }
                }
            }
        """, "new TemplateContributor()")

        when:
        fails("custom")

        then:
        failure.assertHasCause("Could not render custom test report entry 'Template' with TemplateContributor.")
        failure.assertHasCause("The HTML of a custom test report entry must not contain <template> tags, as they could break out of the entry.")

        where:
        tag                                       | appends
        "a template start tag"                    | ["<template>"]
        "a template end tag"                      | ["<p></p></template>"]
        "an upper case template end tag"          | ["</TEMPLATE >"]
        "a template end tag split across appends" | ["<p></tem", "plate", ">"]
        "a template start tag with an attribute"  | ["<template shadowrootmode=open>"]
    }

    def "build fails when contributor leaves #comment"() {
        given:
        buildFile << contributorTask("""
            class CommentContributor implements TestReportEntryContributor {
                String getEntryName() { "Comment" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.html { htmlOut ->
                        ${appends.collect { "htmlOut.append('${it}')" }.join("\n")}
                    }
                }
            }
        """, "new CommentContributor()")

        when:
        fails("custom")

        then:
        failure.assertHasCause("Could not render custom test report entry 'Comment' with CommentContributor.")
        failure.assertHasCause("The HTML of a custom test report entry must not end inside a comment, as it would hide the end of the entry.")

        where:
        comment                                                   | appends
        "an unclosed comment"                                     | ["<p>a</p><!-- unclosed"]
        "an unclosed comment after a closed one"                  | ["<!-- closed --><p>a</p><!-- unclosed"]
        "an unclosed comment with its start split across appends" | ["<!", "-", "- unclosed"]
        "an unclosed comment containing ->"                       | ["<!-- a -> b"]
    }

    def "contributor can write closed comments"() {
        given:
        buildFile << contributorTask("""
            class CommentContributor implements TestReportEntryContributor {
                String getEntryName() { "Comment" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.html { htmlOut ->
                        htmlOut.append("<!-- normal --><!--><!---><!-- bang --!><!-- split -")
                        htmlOut.append("->")
                        htmlOut.append("<p class='after'>after</p>")
                    }
                }
            }
        """, "new CommentContributor()")

        when:
        fails("custom")

        then:
        def results = resultsFor("tests/custom")
        results.assertHtml("suite/withOutput.html", ".custom-entry > template > p.after") {
            assert it*.text() == ["after"]
        }
    }

    def "contributor can write custom elements starting with template"() {
        given:
        buildFile << contributorTask("""
            class TemplateLikeContributor implements TestReportEntryContributor {
                String getEntryName() { "Template like" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.html { htmlOut -> htmlOut.append("<template-like>ok</template-like>") }
                }
            }
        """, "new TemplateLikeContributor()")

        when:
        fails("custom")

        then:
        def results = resultsFor("tests/custom")
        results.assertHtml("suite/withOutput.html", ".custom-entry > template > template-like") {
            assert it*.text() == ["ok"]
        }
    }

    def "build fails when getting contributor entry name fails"() {
        given:
        buildFile << contributorTask("""
            class BoomEntryNameContributor implements TestReportEntryContributor {
                String getEntryName() { throw new RuntimeException("boom!") }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.empty()
                }
            }
        """, "new BoomEntryNameContributor()")

        when:
        fails("custom")

        then:
        failure.assertHasCause("Could not get the entry name of test report entry contributor BoomEntryNameContributor.")
        failure.assertHasCause("boom!")
    }

    def "contributor cannot use #description"() {
        given:
        buildFile << contributorTask("""
            class NamedContributor implements TestReportEntryContributor {
                String getEntryName() { "${entryName}" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.empty()
                }
            }
        """, "new NamedContributor()")

        when:
        fails("custom")

        then:
        failure.assertHasCause(expectedMessage)

        where:
        description                              | entryName         | expectedMessage
        "an empty entry name"                    | ""                | "Entry name of test report entry contributor NamedContributor must not be blank."
        "a blank entry name"                     | "   "             | "Entry name of test report entry contributor NamedContributor must not be blank."
        "an entry name with leading whitespace"  | " Custom"         | "Entry name ' Custom' of test report entry contributor NamedContributor must not have leading or trailing whitespace."
        "an entry name with trailing whitespace" | "Custom "         | "Entry name 'Custom ' of test report entry contributor NamedContributor must not have leading or trailing whitespace."
        "a reserved single-word entry name"      | "summary"         | "Entry name 'summary' of test report entry contributor NamedContributor is reserved for a built-in entry of the test report."
        "a reserved multi-word entry name"       | "standard output" | "Entry name 'standard output' of test report entry contributor NamedContributor is reserved for a built-in entry of the test report."
    }

    def "contributors cannot use the same entry name"() {
        given:
        buildFile << contributorTask("""
            class FirstContributor implements TestReportEntryContributor {
                String getEntryName() { "Custom" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.empty()
                }
            }

            class SecondContributor implements TestReportEntryContributor {
                String getEntryName() { "Custom" }

                TestReportEntryContent contentFor(TestReportEntryContext context) {
                    TestReportEntryContent.empty()
                }
            }
        """, "new FirstContributor(), new SecondContributor()")

        when:
        fails("custom")

        then:
        failure.assertHasCause("Entry name 'Custom' of test report entry contributor SecondContributor is already used by test report entry contributor FirstContributor.")
    }

    /**
     * A task named "custom" that reports a suite with a passing test with output and a failing test, using the given contributors.
     */
    def contributorTask(@GroovyBuildScriptLanguage String contributorClasses, @GroovyBuildScriptLanguage String contributorInstances) {
        """
            ${contributorClasses}

            abstract class ContributorCustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "custom",
                        getLayout().getBuildDirectory().dir("test-results/custom").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/custom").get(),
                        true,
                        [${contributorInstances}]
                    )) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("withOutput", "withOutput")) {
                                 myTest.started(Instant.now())
                                 myTest.output(Instant.now(), org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut, "out 1")
                                 myTest.output(Instant.now(), org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr, "err 1")
                                 myTest.output(Instant.now(), org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut, "out 2")
                                 myTest.succeeded(Instant.now())
                            }
                            try (def myTest = mySuite.reportTest("failing", "failing")) {
                                 myTest.started(Instant.now())
                                 myTest.failed(Instant.now(), "failure message")
                            }
                            mySuite.failed(Instant.now())
                       }
                       reporter.failed(Instant.now())
                   }
                }
            }

            tasks.register("custom", ContributorCustomTestTask)
        """
    }

    def passingTask(String name, boolean print = false) {
        assert !name.toCharArray().any { it.isWhitespace() }

        """
            abstract class ${name}CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "${name}",
                        getLayout().getBuildDirectory().dir("test-results/${name}").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/${name}").get()
                    )) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("${name} suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("${name} test", "passing test")) {
                                 myTest.started(Instant.now())
                                 ${print ? 'myTest.output(Instant.now(), org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut, "standard out text")' : ""}
                                 ${print ? 'myTest.output(Instant.now(), org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr, "standard error text")' : ""}
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                       }
                       reporter.succeeded(Instant.now())
                   }
                }
            }

            tasks.register("${name}", ${name}CustomTestTask)
        """
    }

    def failingTask(String name) {
        assert !name.toCharArray().any { it.isWhitespace() }

        """
            abstract class ${name}CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "${name}",
                        getLayout().getBuildDirectory().dir("test-results/${name}").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/${name}").get()
                    )) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("${name} suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("${name} test", "failing test")) {
                                 myTest.started(Instant.now())
                                 myTest.failed(Instant.now(), "failure message")
                            }
                            mySuite.failed(Instant.now())
                       }
                       reporter.failed(Instant.now())
                   }
                }
            }

            tasks.register("${name}", ${name}CustomTestTask)
        """
    }

    def failingWithoutFailedTestsTask(String name) {
        assert !name.toCharArray().any { it.isWhitespace() }

        """
            abstract class ${name}CustomTestTask extends DefaultTask {
                @Inject
                abstract TestEventReporterFactory getTestEventReporterFactory()

                @Inject
                abstract ProjectLayout getLayout()

                @TaskAction
                void runTests() {
                    try (def reporter = testEventReporterFactory.createTestEventReporter(
                        "${name}",
                        getLayout().getBuildDirectory().dir("test-results/${name}").get(),
                        getLayout().getBuildDirectory().dir("reports/tests/${name}").get()
                    )) {
                       reporter.started(Instant.now())
                       try (def mySuite = reporter.reportTestGroup("${name} suite")) {
                            mySuite.started(Instant.now())
                            try (def myTest = mySuite.reportTest("${name} test", "passing test")) {
                                 myTest.started(Instant.now())
                                 myTest.succeeded(Instant.now())
                            }
                            mySuite.succeeded(Instant.now())
                       }
                       reporter.failed(Instant.now(), "execution failed")
                   }
                }
            }

            tasks.register("${name}", ${name}CustomTestTask)
        """
    }
}
