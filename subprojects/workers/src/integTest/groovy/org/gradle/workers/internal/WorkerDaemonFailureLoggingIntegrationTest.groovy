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

import org.gradle.api.internal.tasks.execution.ExecuteTaskActionBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.logging.events.StyledTextOutputEvent

class WorkerDaemonFailureLoggingIntegrationTest extends AbstractDaemonWorkerExecutorIntegrationSpec {
    def setup() {
        def workAction = fixture.getWorkActionThatCreatesFiles("NormalWorkAction")
        workAction.writeToBuildFile()
        fixture.withWorkActionClassInBuildScript()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
                additionalForkOptions = { jvmArgs('--not-a-real-argument') }
                workActionClass = ${workAction.name}.class
            }
        """
    }

    def "worker startup failure messages are associated with the task that starts it"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        expect:
        executer.withStackTraceChecksDisabled()
        fails("runInWorker")

        and:
        def taskActionOperation = buildOperations.first(ExecuteTaskActionBuildOperationType) {
            it.displayName == "Execute executeTask for :runInWorker"
        }
        taskActionOperation != null
        def outputProgress = taskActionOperation.progress(StyledTextOutputEvent)
        outputProgress.size() == 3
        outputProgress[0].details.spans[0].text == "Unrecognized option: --not-a-real-argument\n"
        outputProgress[1].details.spans[0].text == "Error: Could not create the Java Virtual Machine.\n"
        outputProgress[2].details.spans[0].text == "Error: A fatal exception has occurred. Program will exit.\n"
    }
}
