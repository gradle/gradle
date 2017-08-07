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

package org.gradle.language

import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.nativeplatform.fixtures.AbstractInstalledToolChainIntegrationSpec

abstract class AbstractNativeParallelIntegrationTest extends AbstractInstalledToolChainIntegrationSpec {
    BuildOperationsFixture buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        executer.withArgument("--max-workers=4")
    }

    void assertTaskIsParallel(String taskName) {
        def task = buildOperations.first(ExecuteTaskBuildOperationType, new Spec<BuildOperationRecord>() {
            @Override
            boolean isSatisfiedBy(BuildOperationRecord record) {
                return record.displayName == "Task :${taskName}"
            }
        })

        assert task != null
        assert buildOperations.getOperationsConcurrentWith(ExecuteTaskBuildOperationType, task).size() > 0
    }

    def withTaskThatRunsParallelWith(String taskName) {
        buildFile << """
            import java.util.concurrent.CountDownLatch
            import org.gradle.internal.work.WorkerLeaseService

            def taskFinished = new CountDownLatch(1)
            gradle.taskGraph.afterTask { task ->
                if (task.name == "${taskName}") {
                    taskFinished.countDown()
                }
            }
            
            task parallelTask {
                dependsOn { tasks.${taskName}.taskDependencies }
                doLast { 
                    services.get(WorkerLeaseService).withoutProjectLock {
                        taskFinished.await() 
                    }
                }
            }
        """
    }
}
