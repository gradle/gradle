/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.execution.DisableOptimizationsForWorkUnitBuildOperationProgressDetails
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class TaskDisableOptimizationsProgressEventIntegrationTest extends AbstractIntegrationSpec {

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "progress event is emitted during execute op if optimizations are disabled for task"() {
        when:
        executer.noDeprecationChecks()
        buildFile << """
            task producer {
                def outputFile = file("out.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.parentFile.mkdirs()
                    outputFile.text = "produced"
                }
            }

            task consumer {
                def inputFile = file("out.txt")
                def outputFile = file("consumerOutput.txt")
                inputs.files(inputFile)
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        then:
        succeeds("producer", "consumer")

        and:
        def executeTaskOp = buildOperations.first(ExecuteTaskBuildOperationType) {
            it.details.taskPath == ":consumer"
        }
        executeTaskOp != null
        def event = executeTaskOp.progress.find {
            it.hasDetailsOfType(DisableOptimizationsForWorkUnitBuildOperationProgressDetails)
        }
        event != null
        event.details.messages == [
            "Task ':consumer' uses the output of task ':producer', without declaring an explicit dependency (using Task.dependsOn() or Task.mustRunAfter()) or an implicit dependency (declaring task ':producer' as an input). This can lead to incorrect results being produced, depending on what order the tasks are executed."
        ]
    }

}
