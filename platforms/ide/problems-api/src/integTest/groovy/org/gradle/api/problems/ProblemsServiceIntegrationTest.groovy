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
import org.gradle.integtests.fixtures.problems.ReceivedProblem

class ProblemsServiceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def withReportProblemTask(String taskActionMethodBody) {
        buildFile """
            import org.gradle.api.problems.internal.Problem
            import org.gradle.api.problems.Severity

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    $taskActionMethodBody
                }
            }

            tasks.register("reportProblem", ProblemReportingTask)
        """
    }

    ReceivedProblem getCollectedProblem() {
        assert this.collectedProblems.size() == 1
        this.collectedProblems[0]
    }

    def "problem replaced with a validation warning if mandatory label definition is missing"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.details('Wrong API usage')
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['label'] == 'problem label must be specified'
        collectedProblem['problemCategory'] == [namespace: 'org.example.plugin', category: 'validation', subCategories: ['problems-api', 'missing-label']]
        collectedProblem['locations'] == [[length: -1, column: -1, line: 12, path: "build file '$buildFile.absolutePath'"], [buildTreePath: ':reportProblem']]
    }

    def "problem replaced with a validation warning if mandatory category definition is missing"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('Wrong API usage')
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['label'] == 'problem category must be specified'
        collectedProblem['problemCategory'] == [namespace: 'org.example.plugin', category: 'validation', subCategories: ['problems-api', 'missing-category']]
        collectedProblem['locations'] == [[length: -1, column: -1, line: 12, path: "build file '$buildFile.absolutePath'"], [buildTreePath: ':reportProblem']]
    }


    def "can emit a problem with minimal configuration"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['label'] == 'label'
        collectedProblem['problemCategory'] == [namespace: 'org.example.plugin', category: 'type', subCategories: []]
        collectedProblem['locations'] == [[buildTreePath: ':reportProblem']]
    }

    def "can emit a problem with stack location"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
                .stackLocation()
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['label'] == 'label'
        collectedProblem['problemCategory'] == [namespace: 'org.example.plugin', category: 'type', subCategories: []]
        collectedProblem['locations'] == [[length: -1, column: -1, line: 12, path: "build file '$buildFile.absolutePath'"], [buildTreePath: ':reportProblem']]
    }

    def "can emit a problem with documentation"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
                .documentedAt("https://example.org/doc")
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['documentationLink']['url'] == 'https://example.org/doc'
    }

    def "can emit a problem with offset location"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
                .offsetInFileLocation("test-location", 1, 2)
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem["locations"] == [['path': 'test-location', 'offset': 1, 'length': 2], [buildTreePath: ':reportProblem']]
    }

    def "can emit a problem with file and line number"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
                .lineInFileLocation("test-location", 1, 2)
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem["locations"] == [["path": "test-location", "line": 1, "column": 2, 'length': -1], ['buildTreePath': ':reportProblem']]
    }

    def "can emit a problem with plugin location specified"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
                .pluginLocation("org.example.pluginid")
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem["locations"] == [["pluginId": "org.example.pluginid"], ['buildTreePath': ':reportProblem']]
    }

    def "can emit a problem with a severity"(Severity severity) {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
                .severity(Severity.${severity.name()})
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['severity'] == severity.name()

        where:
        severity << Severity.values()
    }

    def "can emit a problem with a solution"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
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
                it.label('label')
                .category('type')
                .withException(new RuntimeException("test"))
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem["exception"]["message"] == "test"
        !(collectedProblem["exception"]["stackTrace"] as List<String>).isEmpty()
    }

    def "can emit a problem with additional data"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').reporting {
                it.label('label')
                .category('type')
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
                it.label('label')
                .category('type')
                .additionalData("key", ["collections", "are", "not", "supported", "yet"])
            }
        """

        when:
        run('reportProblem')

        then:
        collectedProblem['label'] == 'ProblemBuilder.additionalData() supports values of type String, but java.util.ArrayList as given.'
        collectedProblem['problemCategory'] == [namespace: 'org.example.plugin', category: 'validation', subCategories: ['problems-api', 'invalid-additional-data']]
        collectedProblem['locations'] == [[length: -1, column: -1, line: 12, path: "build file '$buildFile.absolutePath'"], [buildTreePath: ':reportProblem']]
    }

    def "can throw a problem with a wrapper exception"() {
        given:
        withReportProblemTask """
            problems.forNamespace('org.example.plugin').throwing {
                it.label('label')
                .category('type')
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
                it.label('label')
                .category('type')
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
                    it.label("inner")
                    .category("type")
                    .withException(new RuntimeException("test"))
                }
            } catch (RuntimeException ex) {
                problems.forNamespace("org.example.plugin").rethrowing(ex) {
                    it.label("outer")
                    .category("type")
                }
            }
        """

        when:
        fails('reportProblem')

        then:
        this.collectedProblems.size() == 2
        this.collectedProblems[0]["label"] == "inner"
        this.collectedProblems[1]["label"] == "outer"
    }
}
