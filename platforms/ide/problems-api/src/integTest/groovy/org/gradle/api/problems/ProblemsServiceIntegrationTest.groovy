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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage

import static org.gradle.api.problems.ReportingScript.getProblemReportingScript

class ProblemsServiceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportingScript(taskActionMethodBody)
    }

    def "problem replaced with a validation warning if mandatory id is missing"() {
        given:
        withReportProblemTask """
            problems.getReporter().reporting {
                it.details('Wrong API usage, will not show up anywhere')
            }
        """

        when:
        run('reportProblem')

        then:
        verifyAll(receivedProblem) {
            definition.id.fqid == 'problems-api:missing-id'
            definition.id.displayName == 'Problem id must be specified'
            originLocations.size() == 1
            with(oneLocation(LineInFileLocation)) {
                length == -1
                column == -1
                line == 11
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can emit a problem with minimal configuration"() {
        given:
        withReportProblemTask """
            problems.getReporter().reporting {
                it.id('type', 'label')
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
                line == 11
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can emit a problem with stack location"() {
        given:
        withReportProblemTask """
            problems.getReporter().reporting {
                it.id('type', 'label')
                .stackLocation()
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
                line == 11
                path == "build file '$buildFile.absolutePath'"
            }
        }

    }

    def "can emit a problem with documentation"() {
        given:
        withReportProblemTask """
            problems.getReporter().reporting {
                it.id('type', 'label')
                .documentedAt("https://example.org/doc")
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
            problems.getReporter().reporting {
                it.id('type', 'label')
                .offsetInFileLocation("test-location", 1, 2)
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
                line == 11
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can emit a problem with file and line number"() {
        given:
        withReportProblemTask """
            problems.getReporter().reporting {
                it.id('type', 'label')
                .lineInFileLocation("test-location", 1, 2)
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
                line == 11
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can emit a problem with a severity"(Severity severity) {
        given:
        withReportProblemTask """
            problems.getReporter().reporting {
                it.id('type', 'label')
                .severity(Severity.${severity.name()})
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
            problems.getReporter().reporting {
                it.id('type', 'label')
                .solution("solution")
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
            problems.getReporter().reporting {
                it.id('type', 'label')
                .withException(new RuntimeException("test"))
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
            problems.getReporter().reporting {
                it.id('type', 'label')
                .additionalData(org.gradle.api.problems.GeneralDataSpec) {
                    it.put('key','value')
                }
            }
        """

        when:
        run('reportProblem')

        then:
        receivedProblem.additionalData.asMap == ['key': 'value']
    }

    def "cannot set addtional data with different type"() {
        given:
        withReportProblemTask """
            problems.getReporter().reporting {
                it.id('type', 'label')
                .additionalData(org.gradle.api.problems.internal.GeneralDataSpec) {
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
            problems.getReporter().reporting {
                it.id('type', 'label')
                .additionalData(InvalidData) {}
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
                line == 11
                path == "build file '$buildFile.absolutePath'"
            }
        }
    }

    def "can throw a problem with a wrapper exception"() {
        given:
        withReportProblemTask """
            problems.getReporter().throwing {
                it.id('type', 'label')
                .withException(new RuntimeException('test'))
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
            try {
                problems.getReporter().throwing {
                    it.id('type11', 'inner')
                    .withException(new RuntimeException("test"))
                }
            } catch (RuntimeException ex) {
                problems.getReporter().throwing {
                    it.id('type12', 'outer').withException(ex)
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
            for (int i = 0; i < 10; i++) {
                problems.getReporter().reporting {
                        it.id('type', 'label')
                        .severity(Severity.WARNING)
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
            for (int i = 0; i < 10; i++) {
                problems.getReporter().reporting {
                        it.id("type\$i", "This is the heading problem text\$i")
                        .severity(Severity.WARNING)
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
            for (int i = 0; i < 10; i++) {
                problems.getReporter().reporting {
                        it.id("type\$i", "This is the heading problem text\$i")
                        .severity(Severity.WARNING)
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
}
