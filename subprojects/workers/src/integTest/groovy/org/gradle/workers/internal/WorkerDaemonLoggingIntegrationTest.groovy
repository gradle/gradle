/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Issue

class WorkerDaemonLoggingIntegrationTest extends AbstractDaemonWorkerExecutorIntegrationSpec {
    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @Issue("https://github.com/gradle/gradle/issues/10122")
    def "can log many messages from action in a worker daemon"() {
        def workActionThatProducesLotsOfOutput = fixture.getWorkActionThatCreatesFiles("LotsOfOutputWorkAction")
        workActionThatProducesLotsOfOutput.with {
            action += """
                1000.times {
                    println "foo!"
                }
            """
        }

        fixture.withWorkActionClassInBuildScript()
        workActionThatProducesLotsOfOutput.writeToBuildFile()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                workActionClass = ${workActionThatProducesLotsOfOutput.name}.class
            }
        """

        expect:
        succeeds("runInWorker")

        and:
        def operation = buildOperations.only(ExecuteWorkItemBuildOperationType)
        operation.displayName == "LotsOfOutputWorkAction"
        with (operation.details) {
            className == "LotsOfOutputWorkAction"
            displayName == "LotsOfOutputWorkAction"
        }

        and:
        operation.progress.size() == 1000
    }
}
