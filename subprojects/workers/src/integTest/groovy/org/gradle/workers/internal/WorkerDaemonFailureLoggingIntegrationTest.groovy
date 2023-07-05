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

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture

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

    def "worker startup failure messages are NOT associated with the task that starts it"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        expect:
        executer.withStackTraceChecksDisabled()
        fails("runInWorker")

        and:
        def taskOperation = buildOperations.first(ExecuteTaskBuildOperationType) {
            it.displayName == "Task :runInWorker"
        }
        taskOperation != null
        // there's only the task start progress, no failure progress
        taskOperation.progress.size() == 1
        errorOutput.contains("Unrecognized option: --not-a-real-argument")
    }
}
