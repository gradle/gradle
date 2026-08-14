/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.integtests.fixtures.configurationcache

import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.problems.internal.report.HtmlReportWriter
import org.gradle.problems.internal.report.model.JsDiagnostic
import org.gradle.problems.internal.report.model.JsError
import org.gradle.problems.internal.report.model.JsMessageFragment
import org.gradle.problems.internal.report.model.JsModel
import org.gradle.problems.internal.report.model.JsStackTracePart
import org.gradle.problems.internal.report.model.JsTrace
import org.gradle.problems.internal.report.model.JsTraceField
import org.gradle.problems.internal.report.model.JsTraceTask
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheProblemsFixture.resolveConfigurationCacheReport

/**
 * A unit test for {@link ConfigurationCacheProblemsFixture}
 */
class ConfigurationCacheProblemsFixtureTest extends Specification {
    private static final String NEWLINE = System.lineSeparator()

    @Rule
    TestNameTestDirectoryProvider dirProvider = new TestNameTestDirectoryProvider(getClass())

    private TestFile rootDir = dirProvider.createDir()
    private TestFile reportDir = dirProvider.file("build", "reports", "configuration-cache")
    private TestFile reportFile = reportDir.file("configuration-cache-report.html")
    private def fixture = new ConfigurationCacheProblemsFixture(rootDir)

    ConfigurationCacheReportFixture report() {
        return fixture.htmlReport()
    }

    ConfigurationCacheReportFixture report(TestFile reportFile) {
        return fixture.htmlReport("See the complete report at " + new ConsoleRenderer().asClickableFileUrl(reportFile))
    }

    def "findReportFile finds a single report dir"() {
        given:
        reportFile.setText ""

        expect:
        fixture.findReportFile() == reportFile
    }

    def "findReportFile fails if no base report dir exists"() {
        when:
        fixture.findReportFile()

        then:
        def expectedFailure = thrown(AssertionError)

        expectedFailure.message.startsWith "Configuration cache report directory '$reportDir' not found"
    }

    def "findReportFile fails if multiple report file exist"() {
        given:
        reportDir.createDir()
        reportDir.createDir("dir1").file("configuration-cache-report.html").text = ""
        reportDir.createDir("dir2").file("configuration-cache-report.html").text = ""

        when:
        fixture.findReportFile()

        then:
        def expectedFailure = thrown(AssertionError)

        expectedFailure.message.startsWith "Multiple report files (2) found under $reportDir in dir1/, dir2/"
    }

    def "findReportFile fails if no report file exists"() {
        given:
        reportDir.createDir()

        when:
        fixture.findReportFile()

        then:
        def expectedFailure = thrown(AssertionError)

        expectedFailure.message.startsWith "No report file found under $reportDir"
    }

    def "assertHtmlReportHasProblems fails if number of problems is different than expected"() {
        generateReportFile(0)

        expect:
        report().assertContents {
            totalProblemsCount = 0
        }

        when:
        report().assertContents {
            totalProblemsCount = 1
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <1>${NEWLINE}     but: was <0>"
    }

    def "assertHtmlReportHasNoProblems fails when there are problems"() {
        generateReportFile(1)

        when:
        report().assertHasNoProblems()

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <0>${NEWLINE}     but: was <1>"
    }

    def "assertHtmlReportHasNoProblems fails when there is a report with problems"() {
        given:
        generateReportFile(1)

        when:
        report().assertHasNoProblems()

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <0>${NEWLINE}     but: was <1>"
    }


    def "assertHtmlReportHasNoProblems passes when there is a report with no problems"() {
        given:
        generateReportFile(0)

        expect:
        report().assertHasNoProblems()
    }

    def "resolveConfigurationCacheReport finds report link in console"() {
        generateReportFile(2)
        def output = "See the complete report at ${ConfigurationCacheProblemsFixture.clickableUrlFor(reportFile)}${NEWLINE}"

        expect:
        resolveConfigurationCacheReport(rootDir, output) == reportFile
    }

    def "assertHtmlReportHasProblems validates number of problems"() {
        generateReportFile(2)

        expect:
        report().assertContents {
            problemsWithStackTraceCount = 0
            totalProblemsCount = 2
        }

        when:
        report().assertContents {
            problemsWithStackTraceCount = 0
            totalProblemsCount = 3
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <3>${NEWLINE}     but: was <2>"
    }

    def "assertHtmlReportHasProblems validates number of problems with stacktraces"() {
        generateReportFile(10, 2)

        expect:
        report().assertContents {
            totalProblemsCount = 10
            problemsWithStackTraceCount = 2
        }

        when:
        report().assertContents {
            totalProblemsCount = 10
            problemsWithStackTraceCount = 3
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of problem(s) with stacktrace${NEWLINE}Expected: <3>${NEWLINE}     but: was <2>"
    }

    def "assertHtmlReportHasNoProblems passes when there is a report with no failures"() {
        when:
        report().assertHasNoProblems()

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("Configuration cache report directory '$reportDir' not found.")
    }

    def "assertHtmlReportHasProblems fails when there is no report dir"() {
        expect:
        !reportDir.isDirectory()

        when:
        report(reportFile).assertContents {
            totalProblemsCount = 1
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("HTML report HTML file '$reportFile' not found")
    }

    def "assertHtmlReportHasProblems fails when there is no report file"() {
        given:
        reportDir.createDir()

        when:
        report(reportFile).assertContents {
            totalProblemsCount = 1
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("HTML report HTML file '$reportFile' not found")
    }

    def "assertHtmlReportHasProblems validates unique problems"() {
        generateReportFile(2)

        expect:
        report().assertContents {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2"
            )
        }

        when:
        report().assertContents {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1"
            )
        }

        then:
        def expectedFailure1 = thrown(AssertionError)
        expectedFailure1.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <1>${NEWLINE}     but: was <2>"

        when:
        report().assertContents {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2",
                "Some problem 3"
            )
        }

        then:
        def expectedFailure2 = thrown(AssertionError)
        expectedFailure2.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <3>${NEWLINE}     but: was <2>"

        when:
        report().assertContents {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 3"
            )
        }

        then:
        def expectedFailure3 = thrown(AssertionError)
        expectedFailure3.message.startsWith("Expected problem message at #1 to be a string starting with \"Some problem 3\", but was: Some problem 2.")
    }

    def "assertHtmlReportHasProblems allows ignoring non-unique problems"() {
        generateReportFile(
            ["Some problem 1", "Some problem 2", "Some problem 2"]
        )

        expect:
        report().assertContents {
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2"
            )
            totalProblemsCount = 3
            problemsWithStackTraceCount = 0
        }

        and:
        report().assertContents {
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2"
            )
            enforceTotalProblemCount = false
            problemsWithStackTraceCount = 0
        }

        // failing to expect the right totalProblemCount and not ignoring duplicates fails as expected
        when:
        report().assertContents {
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2"
            )
            problemsWithStackTraceCount = 0
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message== "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <2>${NEWLINE}     but: was <3>"
    }

    def "assertHtmlReportHasProblems validates traces in unique problems"() {
        when:
        generateReportFile(2)
        report().assertContents {
            problemsWithStackTraceCount = 0
            withProblem("Some problem 1")
            withProblem("Some problem 2") {
                at("foo").at("bar")
            }
        }
        then:
        def expectedFailure0 = thrown(AssertionError)
        expectedFailure0.message.contains("Expected at most 0 location matchers for problem #1 but got 2.")

        when:
        generateReportFile(
            ["Some problem 1"],
            [[task(":myTask1")]]
        )
        report().assertContents {
            problemsWithStackTraceCount = 0
            withProblem("Some problem 1") {
                at(":myTask2")
            }
        }
        then:
        def expectedFailure1 = thrown(AssertionError)
        expectedFailure1.message.contains("Expected trace for problem #0 at position 0 to match a string starting with \":myTask2\", but was: :myTask1.")

        expect:
        generateReportFile(
            ["Some problem 1", "Some problem 2"],
            [
                null,
                [task(":myTask"), field("field1")]
            ]
        )
        report().assertContents {
            problemsWithStackTraceCount = 0
            withProblem("Some problem 1")
            withProblem("Some problem 2") {
                at("field1").at(":myTask")
            }
        }

        and:
        generateReportFile(
            [
                "Some problem 1",
                "Some problem 1",
                "Some problem 1"
            ],
            [
                [task(":myTask1"), field("field1")],
                [task(":myTask2"), field("field1")],
                [task(":myTask2"), field("field2")],
            ]
        )
        report().assertContents {
            problemsWithStackTraceCount = 0
            withProblem("Some problem 1") {
                at("field1").at(":myTask1")
            }
            withProblem("Some problem 1") {
                at("field1").at(":myTask2")
            }
            withProblem("Some problem 1") {
                at("field2").at(":myTask2")
            }
        }
    }

    def "assertHtmlReportHasProblems validates unique problems in order"() {
        generateReportFile(["Some problem 1", "Some problem 2"])

        when:
        report().assertContents {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                // expect problems in wrong order
                "Some problem 2",
                "Some problem 1"
            )
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("Expected problem message at #0 to be a string starting with \"Some problem 2\", but was: Some problem 1.")

        expect:
        report().assertContents {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2"
            )
        }
    }

    private TestFile generateReportFile(int problems, int problemsWithStacktrace = 0) {
        assert problemsWithStacktrace <= problems
        def problemMessages = (0..<problems).collect { "Some problem ${it+1}".toString() }
        def problemStacktraces = (0..<problemsWithStacktrace).collect { index ->
            ["somePart"]
        }
        generateReportFile(problemMessages, [], problemStacktraces)
    }

    private TestFile generateReportFile(List<String> problemMessages, List<List<JsTrace>> problemsAndPropertyTraces = [], List<List<String>> problemsAndStacktraceParts = []) {
        def diagnostics = problemMessages.withIndex().collect { String problemText, int index ->
            problemDiagnostic(problemText, problemsAndPropertyTraces[index], problemsAndStacktraceParts[index])
        }

        // Write the report the same way a build does, so that this test cannot drift away from the
        // format the fixture has to read.
        reportFile.parentFile.mkdirs()
        def writer = new HtmlReportWriter(reportFile.newWriter("utf-8"))
        writer.beginHtmlReport()
        diagnostics.each { writer.writeDiagnostic(it) }
        writer.endHtmlReport(reportModel(problemMessages.size()))
        writer.close()

        return reportFile
    }

    // The model classes are Kotlin data classes with default values, which Groovy cannot use, so
    // every property has to be passed positionally.
    private static JsDiagnostic problemDiagnostic(String problemText, List<JsTrace> traces, List<String> stacktraceParts) {
        return new JsDiagnostic(
            null, // input
            [new JsMessageFragment(problemText, null)], // problem
            null, // incompatibleTask
            traces ?: [],
            null, // documentationLink
            stacktraceParts == null ? null : new JsError(
                null, // summary
                stacktraceParts.collect { new JsStackTracePart(it, null) }
            )
        )
    }

    private static JsTrace task(String path) {
        return new JsTraceTask(path, "org.gradle.api.DefaultTask")
    }

    private static JsTrace field(String name) {
        return new JsTraceField(name, "com.example.SomeBean")
    }

    private static JsModel reportModel(int problemCount) {
        return new JsModel(
            null, // buildName
            "storing", // cacheAction
            null, // requestedTasks
            null, // cacheActionDescription
            "https://docs.gradle.org/current/userguide/configuration_cache.html",
            problemCount, // totalProblemCount
            problemCount, // uniqueProblemCount
            0, // overflownProblemCount
            [] // diagnostics, streamed separately
        )
    }
}

