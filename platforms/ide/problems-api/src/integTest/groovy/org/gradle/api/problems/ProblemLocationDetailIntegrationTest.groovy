/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.problems

import groovy.transform.TupleConstructor
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.api.problems.internal.DefaultProblemProgressDetails
import org.gradle.api.problems.internal.StackTraceLocation
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.problems.ReceivedProblem

class ProblemLocationDetailIntegrationTest extends AbstractIntegrationSpec {

    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def "location detail decreases as the stack capturing budgets are spent"() {
        given:
        settingsFile "rootProject.name = 'root'"
        buildFile """
            def reporter = services.get(${Problems.name}).getReporter()
            def group = ${ProblemGroup.name}.create('demo', 'demo group')
            def problemId = { String id -> ${ProblemId.name}.create(id, id, group) }
        """
        // A problem is located where `report` is called, so each call has to be on its own line.
        6.times {
            buildFile "\nreporter.report(problemId('issue-${it + 1}')) {}"
        }

        and:
        def reportingLines = buildFileLinesStartingWith("reporter.report(")
        assert reportingLines.size() == 6

        when:
        // The default warning mode lifts the bounded budget, which would hide the last level of detail.
        executer.withWarningMode(WarningMode.Summary)
        // Three full captures and two bounded ones for six problems, so every level is reached.
        executer.withArgument("-Dorg.gradle.internal.problem.diagnostics.stacktrace-count.max=3")
        executer.withArgument("-Dorg.gradle.internal.problem.diagnostics.bounded-captures.max=2")
        succeeds 'help'

        then:
        def located = (1..6).collect { locationReportedFor("issue-$it") }
        // The three full captures and the two bounded ones all say which line the problem came from.
        located[0..4].every { it != null }
        // Past both budgets a problem no longer says where it came from.
        located[5..-1].every { it == null }

        and:
        // Every located problem points at the line of its own `report` call in the build file.
        located[0..4].every { it.file == buildFile.absolutePath }
        located[0..4].collect { it.line } == reportingLines[0..4]

        and:
        // A full capture descends into the Gradle runtime; a bounded one is cut at the calling script.
        located[0..2].every { it.frames > 100 }
        located[3..4].every { it.frames < 20 }
    }

    private List<Integer> buildFileLinesStartingWith(String prefix) {
        def lines = buildFile.text.readLines()
        return (1..lines.size()).findAll { lines[it - 1].startsWith(prefix) }
    }

    private ReportedLocation locationReportedFor(String problemId) {
        def problem = buildOperations.progress(DefaultProblemProgressDetails).details
            .collect { new ReceivedProblem(0, it['problem'] as Map<String, Object>) }
            .find { it.definition.id.name == problemId }
        def location = problem.allLocations(StackTraceLocation).find { it.fileLocation != null }
        if (location == null) {
            return null
        }
        def fileLocation = location.fileLocation as LineInFileLocation
        return new ReportedLocation(fileLocation.path, fileLocation.line, location.stackTrace.size())
    }

    @TupleConstructor
    private static class ReportedLocation {
        final String file
        final int line
        final int frames
    }
}
