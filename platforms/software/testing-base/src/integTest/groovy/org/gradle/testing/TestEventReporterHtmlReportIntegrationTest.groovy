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
import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.internal.GUtil

import static org.gradle.util.Matchers.containsText
import static org.hamcrest.CoreMatchers.equalTo

class TestEventReporterHtmlReportIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.CUSTOM
    }

    def "successful tests do not emit HTML reports to console"() {
        given:
        buildFile << passingTask("passing")

        when:
        succeeds("passing")

        then:
        outputDoesNotContain("See the test results for more details")
        outputDoesNotContain("Aggregate test results")

        // Aggregate results are still emitted even if we don't print the URL to console
        aggregateResults()
            .testPath(":passing suite")
            .onlyRoot()
            .assertChildCount(1, 0)
    }

    def "HTML report contains output at task level only"() {
        given:
        buildFile << passingTask("passing", true)

        when:
        succeeds("passing")

        then:
        def results = aggregateResults()
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
        def results = aggregateResults()
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
        def results = aggregateResults()
        results
            .testPath(":doubled suite")
            .onlyRoot()
            .assertOnlyChildrenExecuted("doubled test", "doubled test")
            .assertMetadata(ImmutableListMultimap.of(
                "index", "0",
                "index", "1"
            ).entries().toList())
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
        aggregateResults()
            .testPath(":failing suite")
            .onlyRoot()
            .assertChildCount(1, 1)
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
        def aggregateResults = aggregateResults()
        aggregateResults.testPath(":passing suite")
            .onlyRoot()
            .assertChildCount(1, 0)
        aggregateResults.testPath(":failing suite")
            .onlyRoot()
            .assertChildCount(1, 1)
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

        def aggregateReportFile = file("build/reports/aggregate-test-results/index.html")
        def renderedUrl = new ConsoleRenderer().asClickableFileUrl(aggregateReportFile);
        outputContains("Aggregate test results: " + renderedUrl)
        def aggregateResults = aggregateResults()
        aggregateResults.testPath(":failing1 suite")
            .onlyRoot()
            .assertChildCount(1, 1)
        aggregateResults.testPath(":failing2 suite")
            .onlyRoot()
            .assertChildCount(1, 1)
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

    def "aggregate test results has roots in order by path to roots"() {
        given:
        buildFile << passingTask("aFirst")
        buildFile << failingTask("bSecond")
        buildFile << failingTask("cThird")
        buildFile << passingTask("dFourth")

        when:
        fails("--continue", "aFirst", "bSecond", "cThird", "dFourth")

        then:
        failure.assertHasFailures(2)

        def aggregateResults = aggregateResults('', GenericTestExecutionResult.TestFramework.CUSTOM)
        assert aggregateResults.testPath(":").rootNames == ["aFirst", "bSecond", "cThird", "dFourth"]
    }

    def "aggregate report with roots of same name disambiguates the roots and has both results"() {
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
        def results = aggregateResults()
        results
            .testPath(":TheSameName suite")
            .root("TheSameName (1)")
            .assertChildCount(1, 0)
            .assertOnlyChildrenExecuted("theSameName1 test")
        results
            .testPath(":TheSameName suite")
            .root("TheSameName (2)")
            .assertChildCount(1, 0)
            .assertOnlyChildrenExecuted("theSameName2 test")
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
}
