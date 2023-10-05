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

class ProblemsServiceIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
        buildFile << """
            tasks.register("reportProblem", ProblemReportingTask)
        """
    }

    def "can emit a problem with mandatory fields"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .noLocation()
                        .category("type")
                        }.report();
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["label"] == "label"
        this.collectedProblems[0]["problemCategory"]["category"] == "type"
    }

    def "can emit a problem with user-manual documentation"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .documentedAt(
                            Documentation.userManual("test-id", "test-section")
                        )
                        .noLocation()
                        .category("type")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        def link = this.collectedProblems[0]["documentationLink"]
        link["properties"]["page"] == "test-id"
        link["properties"]["section"] == "test-section"
        link["url"].startsWith("https://docs.gradle.org")
        link["consultDocumentationMessage"].startsWith("For more information, please refer to https://docs.gradle.org")
    }

    def "can emit a problem with upgrade-guide documentation"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .documentedAt(
                            Documentation.upgradeGuide(8, "test-section")
                        )
                        .noLocation()
                        .category("type")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        def link = this.collectedProblems[0]["documentationLink"]
        link["properties"]["page"] == "upgrading_version_8"
        link["properties"]["section"] == "test-section"
        link["url"].startsWith("https://docs.gradle.org")
        link["consultDocumentationMessage"].startsWith("Consult the upgrading guide for further information: https://docs.gradle.org")
    }

    def "can emit a problem with dsl-reference documentation"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .documentedAt(
                            Documentation.dslReference(Problem.class, "label")
                        )
                        .noLocation()
                        .category("type")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        def link = this.collectedProblems[0]["documentationLink"]
        link["properties"]["targetClass"] == Problem.class.name
        link["properties"]["property"] == "label"
    }

    def "can emit a problem with partially specified location"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .location("test-location", 1)
                        .category("type")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["where"][0] == [
            "type": "file",
            "path": "test-location",
            "line": 1,
            "column": null,
            "length": 0
        ]
    }

    def "can emit a problem with fully specified location"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .location("test-location", 1, 1)
                        .category("type")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["where"][0] == [
            "type": "file",
            "path": "test-location",
            "line": 1,
            "column": 1,
            "length": 0
        ]

        def taskPath = this.collectedProblems[0]["where"][1]
        taskPath["type"] == "task"
        taskPath["identityPath"]["absolute"] == true
        taskPath["identityPath"]["path"] == ":reportProblem"
    }

    def "can emit a problem with plugin location specified"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .pluginLocation("org.example.pluginid")
                        .category("type")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        def problem = this.collectedProblems[0]

        def fileLocation = problem["where"][0]
        fileLocation["type"] == "pluginId"
        fileLocation["pluginId"] == "org.example.pluginid"
    }

    def "can emit a problem with a severity"(Severity severity) {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .noLocation()
                        .category("type")
                        .solution("solution")
                        .severity(Severity.${severity.name()})
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["severity"] == severity.name()

        where:
        severity << Severity.values()
    }

    def "can emit a problem with a solution"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .noLocation()
                        .category("type")
                        .solution("solution")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["solutions"] == [
            "solution"
        ]
    }

    def "can emit a problem with exception cause"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .noLocation()
                        .category("type")
                        .withException(new RuntimeException("test"))
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["exception"]["message"] == "test"
        !(this.collectedProblems[0]["exception"]["stackTrace"] as List<String>).isEmpty()
    }

    def "can emit a problem with additional data"() {
        given:
        buildFile << """
            import org.gradle.api.problems.Problem
            import org.gradle.api.problems.Severity
            import org.gradle.internal.deprecation.Documentation

            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    Problem problem = problems.createProblem{
                        it.label("label")
                        .undocumented()
                        .noLocation()
                        .category("type")
                        .additionalData("key", "value")
                        }.report()
                }
            }
            """

        when:
        run("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["additionalData"] == [
            "key": "value"
        ]
    }

    def "can throw a problem with a wrapper exception"() {
        given:
        buildFile << """
            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    RuntimeException exception = new RuntimeException("test")
                    problems.throwing {
                        spec -> spec
                            .label("label")
                            .undocumented()
                            .noLocation()
                            .category("type")
                            .withException(exception)
                    }
                }
            }
            """

        when:
        fails("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["exception"]["message"] == "test"
    }

    def "can rethrow a problem with a wrapper exception"() {
        given:
        buildFile << """
            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    RuntimeException exception = new RuntimeException("test")
                    problems.rethrowing(exception) {
                        spec -> spec
                            .label("label")
                            .undocumented()
                            .noLocation()
                            .category("type")
                    }
                }
            }
            """

        when:
        fails("reportProblem")

        then:
        this.collectedProblems.size() == 1
        this.collectedProblems[0]["exception"]["message"] == "test"
    }

    def "can rethrow a problem with a wrapper exception"() {
        given:
        buildFile << """
            abstract class ProblemReportingTask extends DefaultTask {
                @Inject
                protected abstract Problems getProblems();

                @TaskAction
                void run() {
                    try {
                        RuntimeException exception = new RuntimeException("test")
                        problems.throwing { spec -> spec
                            .label("inner")
                            .undocumented()
                            .noLocation()
                            .category("type")
                            .withException(exception)
                        }
                    } catch (Exception ex) {
                        problems.rethrowing(ex) { spec -> spec
                            .label("outer")
                            .undocumented()
                            .noLocation()
                            .category("type")
                        }
                    }
                }
            }
            """

        when:
        fails("reportProblem")

        then:
        this.collectedProblems.size() == 2
        this.collectedProblems[0]["label"] == "inner"
        this.collectedProblems[1]["label"] == "outer"
    }

}
