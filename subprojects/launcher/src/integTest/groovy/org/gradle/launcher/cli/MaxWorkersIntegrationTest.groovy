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

package org.gradle.launcher.cli

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.work.WorkerLeaseService

class MaxWorkersIntegrationTest extends AbstractIntegrationSpec {
    def "max workers is honored when it changes between invocations"() {
        buildFile << """
            import ${WorkerLeaseService.class.name}
            import ${BuildOperationExecutor.class.name}
            import ${RunnableBuildOperation.class.name}
            import ${BuildOperationDescriptor.class.name}
            import ${BuildOperationContext.class.name}
            import java.util.concurrent.CountDownLatch
            import java.util.concurrent.TimeUnit

            tasks.addRule("") { taskName ->
                if (taskName.startsWith("verifyMaxWorkers")) {
                    task(taskName) { task ->
                        doLast {
                            def count = (taskName - "verifyMaxWorkers") as int
                            def latch = new CountDownLatch(count)
                            assert task.services.get(ParallelismConfiguration).maxWorkerCount == count
                            assert task.services.get(WorkerLeaseService).maxWorkerCount == count
                            task.services.get(BuildOperationExecutor).runAll { queue ->
                                count.times { i ->
                                    queue.add(new RunnableBuildOperation() {
                                        @Override
                                        public BuildOperationDescriptor.Builder description() {
                                            return BuildOperationDescriptor.displayName("test operation")
                                        }

                                        @Override
                                        public void run(BuildOperationContext context) {
                                            latch.countDown()
                                        }
                                    })
                                }
                            }
                            latch.await(10, TimeUnit.SECONDS)
                        }
                    }
                }
            }
        """

        when:
        args("--max-workers=2")

        then:
        succeeds "verifyMaxWorkers2"

        when:
        args("--max-workers=4")

        then:
        succeeds "verifyMaxWorkers4"

        when:
        args("--max-workers=3")

        then:
        succeeds "verifyMaxWorkers3"
    }
}
