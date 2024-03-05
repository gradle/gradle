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
            problems.forNamespace('org.example.plugin').reporting {
                it.details('Wrong API usage')
            }
        """

        when:
        run('reportProblem')

        then:
        def problem = collectedProblem
        problem['definition']['id'] == [
            name: 'missing-id',
            displayName: 'Problem id must be specified',
            parent: [
                'name': 'problems-api',
                'displayName': 'Problems API',
                'parent': null
            ]
        ]
        problem['locations'] == [
            [length: -1, column: -1, line: 11, path: "build file '$buildFile.absolutePath'"]]
    }

    def "can emit a problem with minimal configuration"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
            }
        """

        when:
        run('reportProblem')

        then:
        def problem = collectedProblem
        problem['definition']['id'] == [
            name: 'type',
            displayName: 'label',
            parent: [
                name: 'generic',
                displayName: 'Generic',
                parent: null
            ]
        ]
        problem['locations'] == [
            [length: -1, column: -1, line: 11, path: "build file '$buildFile.absolutePath'"]]
    }

    def "can emit a problem with stack location"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .stackLocation()
            }
        """

        when:
        run('reportProblem')


        then:
        def problem = collectedProblem
        problem['definition']['id'] == [
            name: 'type',
            displayName: 'label',
            parent: [
                name: 'generic',
                displayName: 'Generic',
                parent: null
            ]
        ]
        problem['locations'] == [[length: -1, column: -1, line: 11, path: "build file '$buildFile.absolutePath'"]]
    }

    def "can emit a problem with documentation"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .documentedAt("https://example.org/doc")
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['definition']['documentationLink']['url'] == 'https://example.org/doc'
    }

    def "can emit a problem with offset location"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .offsetInFileLocation("test-location", 1, 2)
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem["locations"] == [['path': 'test-location', 'offset': 1, 'length': 2],
                                          [length: -1, column: -1, line: 11, path: "build file '$buildFile.absolutePath'"]]
    }

    def "can emit a problem with file and line number"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .lineInFileLocation("test-location", 1, 2)
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem["locations"] == [["path": "test-location", "line": 1, "column": 2, 'length': -1],
                                          [length: -1, column: -1, line: 11, path: "build file '$buildFile.absolutePath'"]]
    }

    def "can emit a problem with plugin location specified"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .pluginLocation("org.example.pluginid")
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem["locations"] == [
            ["pluginId": "org.example.pluginid"],
            [length: -1, column: -1, line: 11, path: "build file '$buildFile.absolutePath'"]]
    }

    def "can emit a problem with a severity"(Severity severity) {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .severity(Severity.${severity.name()})
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['definition']['severity'] == severity.name()

        where:
        severity << Severity.values()
    }

    def "can emit a problem with a solution"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .solution("solution")
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['solutions'] == ['solution']
    }

    def "can emit a problem with exception cause"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .withException(new RuntimeException("test"))
            }
        """

        when:
        run('reportProblem')

        then:
        def problem = collectedProblem
        problem["exception"]["message"] == "test"
        !(problem["exception"]["stackTrace"] as List<String>).isEmpty()
    }

    def "can emit a problem with additional data"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .additionalData('key', 'value')
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['additionalData'] == ['key': 'value']
    }

    def "cannot emit a problem with invalid additional data"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.id('type', 'label')
                .additionalData("key", ["collections", "are", "not", "supported", "yet"])
            }
        """

        when:
        run('reportProblem')


        then:
        def problem = collectedProblem
        problem['definition']['id'] == [
            name: 'invalid-additional-data',
            displayName: 'ProblemBuilder.additionalData() only supports values of type String',
            parent: [
                name: 'problems-api',
                displayName: 'Problems API',
                parent: null
            ]
        ]
        problem['locations'] == [[length: -1, column: -1, line: 11, path: "build file '$buildFile.absolutePath'"]]
    }

    def "can throw a problem with a wrapper exception"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').throwing {
                it.id('type', 'label')
                .withException(new RuntimeException('test'))
            }
        """

        when:
        fails('reportProblem')

        then:
        collectedProblem['exception']['message'] == 'test'
    }

    def "can rethrow an exception"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').rethrowing(new RuntimeException("test")) {
                it.id('type', 'label')
            }
        """

        when:
        fails('reportProblem')

        then:
        collectedProblem['exception']['message'] == 'test'
    }

    def "can rethrow a caught exception"() {
        given:
        withReportProblemTask """
            try {
                problems.forNamespace("org.example.plugin").throwing {
                    it.id('type', 'inner')
                    .withException(new RuntimeException("test"))
                }
            } catch (RuntimeException ex) {
                problems.forNamespace("org.example.plugin").rethrowing(ex) {
                    it.id('type', 'outer')
                }
            }
        """

        when:
        fails('reportProblem')

        then:
        this.collectedProblems.size() == 2
        this.collectedProblems[0]['definition']['id']['displayName'] == "inner"
        this.collectedProblems[1]['definition']['id']['displayName'] == "outer"
    }

    def "problem progress events are not aggregated"() {
        given:
        withReportProblemTask """
            for (int i = 0; i < 10; i++) {
                problems.forNamespace("org.example.plugin").reporting {
                        it.id('type', 'label')
                        .severity(Severity.WARNING)
                        .solution("solution")
                }
            }
        """

        when:
        run("reportProblem")

        then:
        def problems = this.collectedProblems
        problems.size() == 10
        problems.every {
            it['definition']['id']["displayName"] == "label" &&
            it['definition']["id"]['name'] == 'type' &&
            it['definition']["severity"] == "WARNING" &&
            it['solutions'] == ["solution"]
        }
    }
}
