/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.operations

import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class MaxWorkersTest extends ConcurrentSpec {

    def "BuildOperationProcessor operation start blocks when there are no leases available, taken by BuildOperationWorkerRegistry"() {
        given:
        def maxWorkers = 1
        def registry = new DefaultBuildOperationWorkerRegistry(maxWorkers)
        def processor = new DefaultBuildOperationProcessor(new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new DefaultBuildOperationQueueTest.SimpleWorker()

        when:
        async {
            start {
                def cl = registry.operationStart()
                instant.worker1
                thread.block()
                instant.worker1Finished
                cl.operationFinish()
            }
            start {
                thread.blockUntil.worker1
                processor.run(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run() {
                            instant.worker2
                        }
                    })
                })
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "BuildOperationWorkerRegistry operation start blocks when there are no leases available, taken by BuildOperationProcessor"() {
        given:
        def maxWorkers = 1
        def registry = new DefaultBuildOperationWorkerRegistry(maxWorkers)
        def processor = new DefaultBuildOperationProcessor(new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new DefaultBuildOperationQueueTest.SimpleWorker()

        when:
        async {
            start {
                processor.run(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run() {
                            instant.worker1
                            thread.block()
                            instant.worker1Finished
                        }
                    })
                })
            }
            start {
                thread.blockUntil.worker1
                def cl = registry.operationStart()
                instant.worker2
                cl.operationFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }
}
