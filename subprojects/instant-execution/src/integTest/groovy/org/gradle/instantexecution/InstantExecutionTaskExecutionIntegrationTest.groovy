/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.instantexecution

class InstantExecutionTaskExecutionIntegrationTest extends AbstractInstantExecutionIntegrationTest {
    def "honors task up-to-date spec"() {
        buildFile << """
            abstract class TaskWithComplexInputs extends DefaultTask {
                @OutputFile
                abstract RegularFileProperty getOutputFile()

                TaskWithComplexInputs() {
                    def result = name == "never"
                    outputs.upToDateWhen { !result }
                }

                @TaskAction
                def go() {
                    outputFile.get().asFile.text = "some-derived-value"
                }
            }

            task never(type: TaskWithComplexInputs) {
                outputFile = layout.buildDirectory.file("never.txt")
            }
            task always(type: TaskWithComplexInputs) {
                outputFile = layout.buildDirectory.file("always.txt")
            }
        """

        when:
        instantRun("never", "always")
        instantRun("never", "always")

        then:
        result.assertTaskSkipped(":always")
        result.assertTasksNotSkipped(":never")
    }
}
