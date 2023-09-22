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

/**
 * Testing if the automatically added problem transformers are present, and if they are working correctly.
 */
class InjectedProblemTransformerIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
        buildFile << """
            tasks.register("reportProblem", ProblemReportingTask)
        """
    }

    def "task is going to be implicitly added to the problem"() {
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
                    problems.createProblem {
                        it.label("label")
                        .undocumented()
                        .noLocation()
                        .type("type")
                    }.report();
                }
            }
            """

        when:
        run("reportProblem")

        then:
        collectedProblems.size() == 1
        def problem = collectedProblems[0]

        def taskPathLocations = problem["where"].findAll {
            it["type"] == "task"
        }
        taskPathLocations.size() == 1

        def taskPathLocation = taskPathLocations[0]
        taskPathLocation["identityPath"]["path"] == ":reportProblem"
    }

}
