/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.problems

import org.gradle.internal.Describables
import org.gradle.internal.code.DefaultUserCodeSource
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.problems.Location
import org.gradle.util.internal.ToBeImplemented
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File


const val REPORT_URL: String = "<<REPORT_URL_PLACEHOLDER>>"

const val ACTION: String = "<<ACTION_PLACEHOLDER>>"

class ConfigurationCacheProblemsSummaryTest {

    @Test
    fun `keeps track of unique problems upto maxCollectedProblems`() {
        val subject = ConfigurationCacheProblemsSummary(maxCollectedProblems = 3)

        // causes for unique problems are collected
        assertTrue(
            "1st problem",
            subject.onProblem(buildLogicProblem("build.gradle", "failure"), ProblemSeverity.Deferred)
        )

        // non-unique problems are not collected and don't count towards the limit
        assertFalse(
            "1st problem (duplicate)",
            subject.onProblem(buildLogicProblem("build.gradle", "failure"), ProblemSeverity.Deferred)
        )
        assertThat(subject.get().reportUniqueProblemCount, equalTo(1))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))

        // hit limit but did not overflow yet
        assertTrue(
            "2nd problem (same message as 1st but different location)",
            subject.onProblem(buildLogicProblem("build.gradle.kts", "failure"), ProblemSeverity.Deferred)
        )
        assertTrue(
            "3nd problem (different message from 1st but same location)",
            subject.onProblem(buildLogicProblem("build.gradle.kts", "failure 2"), ProblemSeverity.Deferred)
        )
        assertThat(subject.get().reportUniqueProblemCount, equalTo(3))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(3))
        assertThat(subject.get().overflownProblemCount, equalTo(0))

        // now we do overflow
        assertFalse(
            "overflow",
            subject.onProblem(buildLogicProblem("build.gradle", "another failure"), ProblemSeverity.Deferred)
        )
        assertThat(subject.get().reportUniqueProblemCount, equalTo(3))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(3))
        assertThat(subject.get().overflownProblemCount, equalTo(1))
    }

    @Test
    fun `keeps track of total problem count regardless of uniqueness`() {
        val subject = ConfigurationCacheProblemsSummary(maxCollectedProblems = 2)
        assertTrue(
            "1st problem",
            subject.onProblem(buildLogicProblem("build.gradle", "failure 1"), ProblemSeverity.Deferred)
        )
        assertTrue(
            "2nd problem",
            subject.onProblem(buildLogicProblem("build.gradle", "failure 2"), ProblemSeverity.Deferred)
        )
        assertFalse(
            "overflow",
            subject.onProblem(buildLogicProblem("build.gradle", "failure 3"), ProblemSeverity.Deferred)
        )

        val summary = subject.get()
        assertThat(
            "Keeps track of total problem count regardless of maxCollectedProblems",
            summary.totalProblemCount,
            equalTo(3)
        )
    }

    @Test
    fun `problems are deduplicated regardless of severity`() {
        val subject = ConfigurationCacheProblemsSummary()
        val trace = buildLogicLocationTrace("build.gradle.kts", 1)
        val severities = listOf(ProblemSeverity.Deferred, ProblemSeverity.Suppressed, ProblemSeverity.SuppressedSilently)
        var count = 0
        val copies = 4
        val uniqueProblems = severities.size
        severities.forEachIndexed { index, severity ->
            (1..copies).forEach { copyIndex ->
                val unique = copyIndex == 1
                val accepted = subject.onProblem(buildLogicProblem(trace, severity.name), severity)
                assertThat(accepted, equalTo(unique))
                val summary = subject.get()
                assertThat(summary.totalProblemCount, equalTo(++count))
                assertThat("$severity $index $copyIndex", summary.reportUniqueProblemCount, equalTo(index + 1))
            }
        }

        // unique problems * copies
        val expectedTotalProblemCount = uniqueProblems * copies
        assertThat(subject.get().totalProblemCount, equalTo(expectedTotalProblemCount))
        // one unique problem per severity
        assertThat(subject.get().reportUniqueProblemCount, equalTo(uniqueProblems))
        // console problems exclude SuppressedSilently
        assertThat(subject.get().consoleProblemCount, equalTo((uniqueProblems - 1) * copies))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo((uniqueProblems - 1)))
    }

    @Test
    fun `console output for only deferred problems`() {
        val subject = ConfigurationCacheProblemsSummary()
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 2), "failure"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 2), "failure"), ProblemSeverity.Deferred)
        checkConsoleText(subject.get(),
            """
            3 problems were found $ACTION the configuration cache, 2 of which seem unique.
            - Build.gradle.kts: line 1: failure
            - Build.gradle.kts: line 2: failure

            See the complete report at $REPORT_URL
            """
        )
    }

    @Test
    fun `console output for only suppressed problems`() {
        val subject = ConfigurationCacheProblemsSummary()
        val problem1 = buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure")
        val problem2 = buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 2), "failure")
        subject.onProblem(problem1, ProblemSeverity.Suppressed)
        subject.onProblem(problem2, ProblemSeverity.Suppressed)
        subject.onProblem(problem2, ProblemSeverity.Suppressed)
        checkConsoleText(subject.get(),
            """
            3 problems were found $ACTION the configuration cache, 2 of which seem unique.
            - Build.gradle.kts: line 1: failure
            - Build.gradle.kts: line 2: failure

            See the complete report at $REPORT_URL
            """
        )
    }

    @Test
    fun `console output for only silently suppressed problems`() {
        val subject = ConfigurationCacheProblemsSummary()
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure"), ProblemSeverity.SuppressedSilently)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 2), "failure"), ProblemSeverity.SuppressedSilently)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 2), "failure"), ProblemSeverity.SuppressedSilently)
        checkConsoleText(subject.get(),
            """
            See the complete report at $REPORT_URL
            """
        )
    }

    @Test
    fun `console output for deferred and silently suppressed problems`() {
        val subject = ConfigurationCacheProblemsSummary()
        val problem0 = buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure")
        val problem1 = buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 2), "failure")
        val problem2 = buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 3), "failure")
        subject.onProblem(problem0, ProblemSeverity.Deferred)
        subject.onProblem(problem1, ProblemSeverity.SuppressedSilently)
        subject.onProblem(problem2, ProblemSeverity.SuppressedSilently)
        subject.onProblem(problem2, ProblemSeverity.SuppressedSilently)
        checkConsoleText(subject.get(),
            """
            1 problem was found $ACTION the configuration cache.
            - Build.gradle.kts: line 1: failure

            See the complete report at $REPORT_URL
            """
        )
    }

    @Test
    fun `console output includes up to MAX_CONSOLE_PROBLEMS non-suppressed problems`() {
        val subject = ConfigurationCacheProblemsSummary()

        // start with MAX_CONSOLE_PROBLEMS (deferred)
        (1..MAX_CONSOLE_PROBLEMS).forEach { index ->
            subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", index), "a deferred problem"), ProblemSeverity.Deferred)
        }

        // add a bunch of silently suppressed problems (should be ignored)
        (100..200).forEach { index ->
            subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", index), "a silently suppressed problem"), ProblemSeverity.SuppressedSilently)
        }

        // expect only MAX_CONSOLE_PROBLEMS (deferred)
        val consoleReportableProblems = (1..MAX_CONSOLE_PROBLEMS)
            .sortedBy {
                // line numbers are sorted as strings, as the base implementation currently does
                it.toString()
            }.joinToString("\n") { index ->
                "- Build.gradle.kts: line $index: a deferred problem"
            }
        checkConsoleText(subject.get(),
            """
$MAX_CONSOLE_PROBLEMS problems were found $ACTION the configuration cache.
$consoleReportableProblems

See the complete report at $REPORT_URL
            """
        )

        // adding one should result in a "plus 1 more problem" message
        subject.onProblem(buildLogicProblem(buildLogicUserCodeSourceTrace("somefile.gradle.kts"), "another deferred problem"), ProblemSeverity.Deferred)
        checkConsoleText(subject.get(),
            """
${MAX_CONSOLE_PROBLEMS + 1} problems were found $ACTION the configuration cache.
$consoleReportableProblems
plus 1 more problem. Please see the report for details.

See the complete report at $REPORT_URL
            """
        )

        // adding another should result in a "plus 2 more problems" message
        subject.onProblem(buildLogicProblem(buildLogicUserCodeSourceTrace("somefile.gradle.kts"), "yet another deferred problem"), ProblemSeverity.Deferred)
        checkConsoleText(subject.get(),
            """
${MAX_CONSOLE_PROBLEMS + 2} problems were found $ACTION the configuration cache.
$consoleReportableProblems
plus 2 more problems. Please see the report for details.

See the complete report at $REPORT_URL
            """
        )
    }

    @Test
    fun `line numbers are taken into account for uniqueness`() {
        val subject = ConfigurationCacheProblemsSummary()
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure"), ProblemSeverity.Deferred)
        assertThat(subject.get().reportUniqueProblemCount, equalTo(1))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))

        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 2), "failure"), ProblemSeverity.Deferred)
        assertThat(subject.get().reportUniqueProblemCount, equalTo(2))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(2))
    }

    @Test
    fun `problem causes are built from problems as expected`() {
        val gradleRuntime = ProblemCause.of(PropertyProblem(PropertyTrace.Gradle, StructuredMessage.forText("failure")))
        assertThat(gradleRuntime.message, equalTo("failure"))
        assertThat(gradleRuntime.userCodeLocation, equalTo("Gradle runtime"))

        val unknown = ProblemCause.of(PropertyProblem(PropertyTrace.Unknown, StructuredMessage.forText("failure")))
        assertThat(unknown.message, equalTo("failure"))
        assertThat(unknown.userCodeLocation, equalTo("unknown location"))

        val projectAtUnknown = ProblemCause.of(PropertyProblem(PropertyTrace.Project(":p1", PropertyTrace.Unknown), StructuredMessage.forText("failure")))
        assertThat(projectAtUnknown.message, equalTo("failure"))
        assertThat(projectAtUnknown.userCodeLocation, equalTo("unknown location"))
    }

    @Test
    fun `problem causes include property trace tail in comparison`() {
        val message = StructuredMessage.forText("")
        val gradle = ProblemCause.of(PropertyProblem(PropertyTrace.Gradle, message))
        val gradle2 = ProblemCause.of(PropertyProblem(PropertyTrace.Gradle, message))
        val unknown = ProblemCause.of(PropertyProblem(PropertyTrace.Unknown, message))
        val project1 = ProblemCause.of(PropertyProblem(PropertyTrace.Project(":p1", PropertyTrace.Gradle), message))
        val project1b = ProblemCause.of(PropertyProblem(PropertyTrace.Project(":p1", PropertyTrace.Gradle), message))
        val project2 = ProblemCause.of(PropertyProblem(PropertyTrace.Project(":p2", PropertyTrace.Gradle), message))
        val project2ThenUnknown = ProblemCause.of(PropertyProblem(PropertyTrace.Project(":p2", PropertyTrace.Unknown), message))
        assertEquals(gradle, gradle2)
        assertNotEquals(gradle, unknown)
        assertEquals(project1, project1b)
        assertNotEquals(project1, project2)
        assertNotEquals(project2, project2ThenUnknown)
        // problem causes that are different may have same shallow versions
        assertEquals(project1.asShallow(), project1.asShallow())
        assertEquals(project1.asShallow(), project2.asShallow())
        // problem causes that do not have deep traces are different from their shallow versions
        assertNotEquals(unknown, unknown.asShallow())
    }

    @Test
    fun `full property trace chain is taken into account for report problem uniqueness`() {
        val subject = ConfigurationCacheProblemsSummary()

        val prop1 = PropertyTrace.VirtualProperty("prop1", PropertyTrace.Gradle)
        val prop2 = PropertyTrace.VirtualProperty("prop2", PropertyTrace.Gradle)
        val prop1InP1a = PropertyTrace.Project(":p1", prop1)
        val prop1InP1b = PropertyTrace.Project(":p1", prop1)
        val prop2InP1 = PropertyTrace.Project(":p1", prop2)
        val prop1InP2 = PropertyTrace.Project(":p2", prop1)
        assertTrue(subject.onProblem(buildLogicProblem(prop1InP1a, "failure"), ProblemSeverity.Deferred))
        assertFalse(subject.onProblem(buildLogicProblem(prop1InP1b, "failure"), ProblemSeverity.Deferred))
        assertThat(subject.get().reportUniqueProblemCount, equalTo(1))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))

        assertTrue(subject.onProblem(buildLogicProblem(prop2InP1, "failure"), ProblemSeverity.Deferred))
        assertThat(subject.get().reportUniqueProblemCount, equalTo(2))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))

        assertTrue(subject.onProblem(buildLogicProblem(prop1InP2, "failure"), ProblemSeverity.Deferred))
        assertThat(subject.get().reportUniqueProblemCount, equalTo(3))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))
    }

    @Test
    fun `location files are taken into account for uniqueness`() {
        val subject = ConfigurationCacheProblemsSummary()
        subject.onProblem(buildLogicProblem("build.gradle.kts", "failure"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem("build.gradle.kts", "failure"), ProblemSeverity.Deferred)
        assertThat(subject.get().reportUniqueProblemCount, equalTo(1))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))

        subject.onProblem(buildLogicProblem("build.gradle", "failure"), ProblemSeverity.Deferred)
        assertThat(subject.get().reportUniqueProblemCount, equalTo(2))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(2))
    }

    @Test
    fun `messages are taken into account for uniqueness`() {
        val subject = ConfigurationCacheProblemsSummary()
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure 1"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure 1"), ProblemSeverity.Deferred)
        assertThat(subject.get().reportUniqueProblemCount, equalTo(1))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))

        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 1), "failure 2"), ProblemSeverity.Deferred)
        assertThat(subject.get().reportUniqueProblemCount, equalTo(2))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(2))
    }

    @Test
    fun `exceptions are NOT taken into account for uniqueness`() {
        val subject = ConfigurationCacheProblemsSummary()
        val exception1 = Throwable()
        val exception2 = Throwable()

        subject.onProblem(buildLogicProblem("build.gradle.kts", "failure", exception1), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem("build.gradle.kts", "failure", exception2), ProblemSeverity.Deferred)
        assertThat(subject.get().reportUniqueProblemCount, equalTo(1))
        assertThat(subject.get().consoleUniqueProblemCount, equalTo(1))
    }

    @Test
    fun `a problem with higher severity replaces a previous problem with the same cause`() {
        val subject = ConfigurationCacheProblemsSummary()

        val problem = buildLogicProblem("build.gradle", "failure")
        subject.onProblem(problem, ProblemSeverity.Suppressed)
        assertThat(subject.get().severityFor(ProblemCause.of(problem)), equalTo(ProblemSeverity.Suppressed))

        subject.onProblem(problem, ProblemSeverity.Deferred)
        assertThat(subject.get().severityFor(ProblemCause.of(problem)), equalTo(ProblemSeverity.Deferred))
    }

    @Test
    @ToBeImplemented("Should sort locations numerically, not lexicographically")
    fun `console problems are sorted by location and message`() {
        val subject = ConfigurationCacheProblemsSummary()

        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 15), "failure 0"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 10), "failure 3"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 10), "failure 1"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 10), "failure 2"), ProblemSeverity.Deferred)
        subject.onProblem(buildLogicProblem(buildLogicLocationTrace("build.gradle.kts", 5), "failure 4"), ProblemSeverity.Deferred)

        checkConsoleText(subject.get(),
            """
            5 problems were found $ACTION the configuration cache.
            - Build.gradle.kts: line 10: failure 1
            - Build.gradle.kts: line 10: failure 2
            - Build.gradle.kts: line 10: failure 3
            - Build.gradle.kts: line 15: failure 0
            - Build.gradle.kts: line 5: failure 4

            See the complete report at $REPORT_URL
            """
        )
        // TODO-RC expected:
//        checkConsoleText(subject.get(),
//            """
//            5 problems were found $ACTION the configuration cache.
//            - Build.gradle.kts: line 5: failure 4
//            - Build.gradle.kts: line 10: failure 1
//            - Build.gradle.kts: line 10: failure 2
//            - Build.gradle.kts: line 10: failure 3
//            - Build.gradle.kts: line 15: failure 0
//
//            See the complete report at $REPORT_URL
//            """
//        )
    }

    private
    fun buildLogicProblem(location: String, message: String, exception: Throwable? = null) = buildLogicProblem(
        buildLogicUserCodeSourceTrace(location),
        message,
        exception
    )

    private
    fun buildLogicProblem(propertyTrace: PropertyTrace, message: String, exception: Throwable? = null) = PropertyProblem(
        propertyTrace,
        StructuredMessage.build { text(message) },
        exception
    )

    private
    fun buildLogicUserCodeSourceTrace(displayName: String): PropertyTrace.BuildLogic =
        PropertyTrace.BuildLogic(DefaultUserCodeSource(Describables.of(displayName), null))

    private
    fun buildLogicLocationTrace(displayName: String, lineNumber: Int): PropertyTrace.BuildLogic =
        PropertyTrace.BuildLogic(Location(Describables.of(displayName), Describables.of(displayName), "/some/path/$displayName", lineNumber))

    private
    fun checkConsoleText(summary: Summary, expected: String) {
        val reportFile = File("report.html")
        val reportFileUrl = ConsoleRenderer().asClickableFileUrl(reportFile)
        val consoleText = summary.textForConsole(ACTION, reportFile)
        assertEquals(
            expected.trimIndent().replace(REPORT_URL, reportFileUrl),
            consoleText.trimIndent()
        )
    }
}
