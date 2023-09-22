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

class ProblemTransformerIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        enableProblemsApiCheck()
    }

    def "task is going to be implicitly added to the problem"() {
        given:
        buildFile << """
            tasks {
                register("failingTask") {
                    doLast {
                        throw new GradleException("my exception")
                    }
                }
            }
        """

        when:
        runAndFail("failingTask")

        then:
        def taskPathLocations = collectedProblems.findAll {
            it["type"] == "task"
        }
        taskPathLocations.size() == 1

        def taskPathLocation = taskPathLocations[0]
        taskPathLocation["path"] == ":failingTask"

    }

}
