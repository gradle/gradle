/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildResultLoggerIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        // Force a forking executer
        // This is necessary since for the embedded executer
        // the Task statistics are not part of the output
        // returned by "this.output"
        executer.requireGradleDistribution()

        file("input.txt") << "data"
        buildFile << """
            task adHocTask {
                def outputFile = file("\$buildDir/output.txt")
                inputs.file(file("input.txt"))
                outputs.file(outputFile)
                doLast {
                    project.mkdir outputFile.parentFile
                    outputFile.text = file("input.txt").text
                }
            }

            task executedTask {
                doLast {
                    println "Hello world"
                }
            }

            task noActions(dependsOn: executedTask) {}
        """
    }

    def "task outcome statistics are reported"() {
        when:
        run "adHocTask", "executedTask"

        then:
        output.contains "2 actionable tasks: 2 executed, 0 avoided (0%)"

        when:
        run "adHocTask", "executedTask"

        then:
        output.contains "2 actionable tasks: 1 executed, 1 avoided (50%)"
    }

    def "tasks with no actions are not counted in stats"() {
        when:
        run "noActions"

        then:
        output.contains "1 actionable task: 1 executed, 0 avoided (0%)"
    }

    def "skipped tasks are not counted"() {
        given:
        executer.withArguments "-x", "executedTask"

        when:
        run "noActions"

        then:
        // No stats are reported because no tasks had any actions
        !output.contains("actionable tasks")
    }
}
