/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.problems.internal.TaskPathLocation
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import spock.lang.Issue

import static org.gradle.api.problems.fixtures.ReportingScript.getProblemReportingScript

class ProblemsServiceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportingScript(taskActionMethodBody)
    }

    def "can emit a problem with minimal configuration"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {}
        """

        when:
        run('reportProblem')

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'generic:type'
            definition.id.displayName == 'label'
            with(oneLocation(LineInFileLocation)) {
                length == -1
                column == -1
                line == 13
                path == "build file '$buildFile.absolutePath'"
            }
            with(oneLocation(TaskPathLocation)) {
                buildTreePath == ':reportProblem'
            }
        }
    }

    // This test will fail when the deprecated space-assignment syntax is removed.
    // Once this happens we need to find another test to validate the behavior.
    @Issue("https://github.com/gradle/gradle/issues/31980")
    def "correct location for space-assignment deprecation"() {
        buildFile '''
            class GroovyTask extends DefaultTask {
                @Input
                def String prop
                void doStuff(Action<Task> action) { action.execute(this) }
            }
            tasks.withType(GroovyTask) { conventionMapping.prop = { '[default]' } }
            task test(type: GroovyTask)
            test {
                description 'does something'
            }
'''

        executer.expectDocumentedDeprecationWarning(
            "Space-assignment syntax in Groovy DSL has been deprecated. " +
                "This is scheduled to be removed in Gradle 10.0. Use assignment ('description = <value>') instead. " +
                "Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#groovy_space_assignment_syntax"
        )

        expect:
        succeeds("test")
        verifyAll(receivedProblem(0)) {
            definition.id.fqid == 'deprecation:space-assignment-syntax-in-groovy-dsl'
            definition.id.displayName == 'Space-assignment syntax in Groovy DSL has been deprecated.'
            def locations = allLocations(LineInFileLocation)
            //guarantee no duplicate locations
            locations.size() == 1
            with(locations) {
                with(get(0)) {
                    length == -1
                    column == -1
                    line == 10
                    path == "build file '$buildFile.absolutePath'"
                }
            }
        }
    }

    def "can emit a problem with stack location"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.stackLocation()
            }
        """

        when:
        run('reportProblem')


        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'generic:type'
            definition.id.displayName == 'label'
            with(oneLocation(LineInFileLocation)) {
                length == -1
                column == -1
                line == 13
                path == "build file '$buildFile.absolutePath'"
            }
        }

    }

    def "can emit a problem with documentation"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.documentedAt("https://example.org/doc")
            }
        """

        when:
        run('reportProblem')

        then:
        receivedProblem.definition.documentationLink.url == 'https://example.org/doc'
    }

    def "can emit a problem with offset location"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.offsetInFileLocation("test-location", 1, 2)
            }
        """

        when:
        run('reportProblem')

        then:
        verifyAll(receivedProblem.originLocations) {
            size() == 2
            with(get(0) as OffsetInFileLocation) {
                path == 'test-location'
                offset == 1
                length == 2
            }
            with(get(1) as LineInFileLocation) {
                length == -1
                column == -1
                line == 13
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can emit a problem with file and line number"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.lineInFileLocation("test-location", 1, 2)
            }
        """

        when:
        run('reportProblem')

        then:
        verifyAll(receivedProblem.originLocations) {
            size() == 2
            with(get(0) as LineInFileLocation) {
                length == -1
                column == 2
                line == 1
                path == 'test-location'
            }
            with(get(1) as LineInFileLocation) {
                length == -1
                column == -1
                line == 13
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can emit a problem with a severity"(Severity severity) {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.severity(Severity.${severity.name()})
            }
        """

        when:
        run('reportProblem')

        then:
        receivedProblem.definition.severity == severity

        where:
        severity << Severity.values()
    }

    def "can emit a problem with a solution"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.solution("solution")
            }
        """

        when:
        run('reportProblem')

        then:
        receivedProblem.solutions == ['solution']
    }

    def "can emit a problem with exception cause"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.withException(new RuntimeException("test"))
            }
        """

        when:
        run('reportProblem')

        then:
        verifyAll(receivedProblem) {
            exception.message == 'test'
            exception.stacktrace.length() > 0
        }
    }

    def "can emit a problem with additional data"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.additionalDataInternal(org.gradle.api.problems.internal.GeneralDataSpec) {
                    it.put('key','value')
                }
            }
        """

        when:
        run('reportProblem')

        then:
        receivedProblem.additionalData.asMap == ['key': 'value']
    }

    def "cannot set additional data with different type"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.additionalData(org.gradle.api.problems.internal.GeneralDataSpec) {
                    it.put('key','value')
                }
                .additionalData(org.gradle.api.problems.internal.DeprecationDataSpec) {
                    it.put('key2','value2')
                }
            }
        """

        when:
        run('reportProblem')

        then:
        thrown(RuntimeException)
    }

    def "cannot emit a problem with invalid additional data"() {
        given:
        buildFile 'class InvalidData implements AdditionalData {}'
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.additionalDataInternal(InvalidData) {}
            }
        """

        when:
        run('reportProblem')

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'problems-api:unsupported-additional-data'
            definition.id.displayName == 'Unsupported additional data type'
            with(oneLocation(LineInFileLocation)) {
                length == -1
                column == -1
                line == 13
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can throw a problem with a wrapper exception"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().throwing(new RuntimeException('test'), problemId) {
            }
        """

        when:
        fails('reportProblem')

        then:
        receivedProblem.exception.message == 'test'
    }

    def "can rethrow a caught exception"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            try {
                problems.getReporter().throwing(new RuntimeException("test"), ${ProblemId.name}.create("type11", "inner", problemGroup)) {
                }
            } catch (RuntimeException ex) {
                problems.getReporter().throwing(ex, ${ProblemId.name}.create("type12", "outer", problemGroup)) {
                }
            }
        """

        when:
        fails('reportProblem')

        then:
        receivedProblem(0).definition.id.displayName == 'inner'
        receivedProblem(1).definition.id.displayName == 'outer'
    }

    def "problem progress events are not aggregated"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            for (int i = 0; i < 10; i++) {
                problems.getReporter().report(problemId) {
                        it.severity(Severity.WARNING)
                        .solution("solution")
                }
            }
        """

        when:
        run("reportProblem")

        then:
        10.times {
            verifyAll(receivedProblem(it)) {
                definition.id.displayName == 'label'
                definition.id.name == 'type'
                definition.severity == Severity.WARNING
                solutions == ["solution"]
            }
        }
    }

    def problemsReportHtmlName = "problems-report.html"
    def problemsReportOutputPrefix = "[Incubating] Problems report is available at: "
    def problemsReportOutputDirectory = "build/reports/problems"

    def "problem progress events in report"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            for (int i = 0; i < 10; i++) {
                problems.getReporter().report(${ProblemId.name}.create("type\$i", "This is the heading problem text\$i", problemGroup)) {
                        it.severity(Severity.WARNING)
                        .details("This is a huge amount of extremely and very relevant details for this problem\$i")
                        .solution("solution")
                }
            }
        """

        when:
        executer.withArgument("--problems-report")
        run("reportProblem")


        then:
        testDirectory.file(problemsReportOutputDirectory, problemsReportHtmlName).exists()

        output.contains(problemsReportOutputPrefix)

        10.times { num ->
            verifyAll(receivedProblem(num)) {
                definition.id.displayName == "This is the heading problem text$num"
                definition.id.name == "type$num"
                definition.severity == Severity.WARNING
                details == "This is a huge amount of extremely and very relevant details for this problem$num"
                solutions == ["solution"]
            }
        }
    }

    def "problem report can be disabled"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            for (int i = 0; i < 10; i++) {
                problems.getReporter().report(${ProblemId.name}.create("type\$i", "This is the heading problem text\$i", problemGroup)) {
                        it.severity(Severity.WARNING)
                        .details("This is a huge amount of extremely and very relevant details for this problem\$i")
                        .solution("solution")
                }
            }
        """

        when:
        executer.withArgument("--no-problems-report")
        run("reportProblem")

        then:
        !testDirectory.file(problemsReportOutputDirectory, problemsReportHtmlName).exists()
        !output.contains(problemsReportOutputPrefix)

        10.times { num ->
            verifyAll(receivedProblem(num)) {
                definition.id.displayName == "This is the heading problem text$num"
                definition.id.name == "type$num"
                definition.severity == Severity.WARNING
                details == "This is a huge amount of extremely and very relevant details for this problem$num"
                solutions == ["solution"]
            }
        }
    }

    static String problemIdScript() {
        """${ProblemGroup.name} problemGroup = ${ProblemGroup.name}.create("generic", "group label");
           ${ProblemId.name} problemId = ${ProblemId.name}.create("type", "label", problemGroup)"""
    }
}
