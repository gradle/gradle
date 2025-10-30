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

    def "findReportDir finds a single report dir"() {
        given:
        reportFile.setText ""

        expect:
        fixture.findReportDir() == reportDir
    }

    def "findReportDir fails if no base report dir exists"() {
        when:
        fixture.findReportDir()

        then:
        def expectedFailure = thrown(AssertionError)

        expectedFailure.message.startsWith "Configuration cache report directory not found at $reportDir"
    }

    def "findReportDir fails if multiple report file exists"() {
        given:
        reportDir.createDir()
        reportDir.createDir("dir1").file("configuration-cache-report.html").text = ""
        reportDir.createDir("dir2").file("configuration-cache-report.html").text = ""

        when:
        fixture.findReportDir()

        then:
        def expectedFailure = thrown(AssertionError)

        expectedFailure.message.startsWith "Multiple report files (2) found under $reportDir - dir1/, dir2/"
    }

    def "findReportDir fails if no report file exists"() {
        given:
        reportDir.createDir()

        when:
        fixture.findReportDir()

        then:
        def expectedFailure = thrown(AssertionError)

        expectedFailure.message.startsWith "No report file found under $reportDir"
    }

    def "assertHtmlReportHasProblems fails if number of problems is different than expected"() {
        generateReportFile(0)

        expect:
        fixture.assertHtmlReportHasProblems {
            totalProblemsCount = 0
        }

        when:
        fixture.assertHtmlReportHasProblems {
            totalProblemsCount = 1
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <1>${NEWLINE}     but: was <0>"
    }

    def "assertHtmlReportHasNoProblems fails when there are problems"() {
        generateReportFile(1)

        when:
        fixture.assertHtmlReportHasNoProblems()

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <0>${NEWLINE}     but: was <1>"
    }

    def "assertHtmlReportHasNoProblems fails when there is a report with problems"() {
        given:
        generateReportFile(1)

        when:
        fixture.assertHtmlReportHasNoProblems()

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <0>${NEWLINE}     but: was <1>"
    }


    def "assertHtmlReportHasNoProblems passes when there is a report with no problems"() {
        given:
        generateReportFile(0)

        expect:
        fixture.assertHtmlReportHasNoProblems()
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
        fixture.assertHtmlReportHasProblems {
            problemsWithStackTraceCount = 0
            totalProblemsCount = 2
        }

        when:
        fixture.assertHtmlReportHasProblems {
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
        fixture.assertHtmlReportHasProblems {
            totalProblemsCount = 10
            problemsWithStackTraceCount = 2
        }

        when:
        fixture.assertHtmlReportHasProblems {
            totalProblemsCount = 10
            problemsWithStackTraceCount = 3
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of problem(s) with stacktrace${NEWLINE}Expected: <3>${NEWLINE}     but: was <2>"
    }

    def "assertHtmlReportHasNoProblems passes when there is a report with no failures"() {
        when:
        fixture.assertHtmlReportHasNoProblems()

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("Configuration cache report directory not found at ${reportDir}.")
    }

    def "assertHtmlReportHasProblems with implicit report dir fails when there is no report"() {
        when:
        fixture.assertHtmlReportHasProblems()

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("Configuration cache report directory not found at ${reportDir}.")
    }

    def "assertHtmlReportHasProblems fails when there is no report dir"() {
        expect:
        !reportDir.isDirectory()

        when:
        fixture.assertHtmlReportHasProblems(reportDir) {
            totalProblemsCount = 1
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("HTML report directory not found '${reportDir}'")
    }

    def "assertHtmlReportHasProblems fails when there is no report file"() {
        given:
        reportDir.createDir()

        when:
        fixture.assertHtmlReportHasProblems(reportDir) {
            totalProblemsCount = 1
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("HTML report HTML file not found in '${reportDir}'")
    }

    def "assertHtmlReportHasProblems validates unique problems"() {
        generateReportFile(2)

        expect:
        fixture.assertHtmlReportHasProblems {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2"
            )
        }

        when:
        fixture.assertHtmlReportHasProblems {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1"
            )
        }

        then:
        def expectedFailure1 = thrown(AssertionError)
        expectedFailure1.message == "HTML report JS model has wrong number of total problem(s)${NEWLINE}Expected: <1>${NEWLINE}     but: was <2>"

        when:
        fixture.assertHtmlReportHasProblems {
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
        fixture.assertHtmlReportHasProblems {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 3"
            )
        }

        then:
        def expectedFailure3 = thrown(AssertionError)
        expectedFailure3.message.startsWith("Expected problem at #1 to be a string starting with \"Some problem 3\", but was: Some problem 2.")
    }

    def "assertHtmlReportHasProblems validates unique problems in order"() {
        generateReportFile(["Some problem 1", "Some problem 2"])

        when:
        fixture.assertHtmlReportHasProblems {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 2",
                "Some problem 1"
            )
        }

        then:
        def expectedFailure = thrown(AssertionError)
        expectedFailure.message.startsWith("Expected problem at #0 to be a string starting with \"Some problem 2\", but was: Some problem 1.")

        expect:
        fixture.assertHtmlReportHasProblems {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2"
            )
        }
    }

    def "assertHtmlReportHasProblems ignores duplicates"() {
        generateReportFile(["Some problem 1",  "Some problem 2", "Some problem 1", "Some problem 3"])

        expect:
        fixture.assertHtmlReportHasProblems {
            problemsWithStackTraceCount = 0
            withUniqueProblems(
                "Some problem 1",
                "Some problem 2",
                "Some problem 3"
            )
        }
    }

    private TestFile generateReportFile(int problems, int problemsWithStacktrace = 0) {
        assert problemsWithStacktrace <= problems
        generateReportFile((0..<problems).collectEntries {index ->
            def hasStackTrace = index < problemsWithStacktrace
            [("Some problem ${index+1}".toString()): hasStackTrace ? ["somePart"] : null]
        })
    }

    private TestFile generateReportFile(List<String> problems) {
        generateReportFile(problems.collectEntries { [(it): null] })
    }

    private TestFile generateReportFile(Map<String, List<String>> problemsAndStacktraces) {

        def jsonData = """
// begin-report-data
{
    "diagnostics": [
        ${
            problemsAndStacktraces.collect { problemText, stacktraceParts ->
                """
                {
                    "problem": [{
                        "text": "${problemText}"
                    }]
                    ${ stacktraceParts?.with { parts -> """,
                        "error": {
                            "parts": [ ${parts.collect { "\"${it}\"" }.join(", ")} ]
                        }
                    """} ?: ""
                    }
                }
                """
            }.join(",${NEWLINE}")
        }
    ]
}
// end-report-data
        """
        reportFile.setText(jsonData)
    }
}

