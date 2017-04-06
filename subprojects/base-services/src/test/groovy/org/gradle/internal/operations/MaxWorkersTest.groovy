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
import org.gradle.internal.progress.TestBuildOperationExecutor
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class MaxWorkersTest extends ConcurrentSpec {

    def "BuildOperationProcessor operation start blocks when there are no leases available, taken by BuildOperationWorkerRegistry"() {
        given:
        def maxWorkers = 1
        def registry = new DefaultBuildOperationWorkerRegistry(maxWorkers)
        def processor = new DefaultBuildOperationProcessor(new TestBuildOperationExecutor(), new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new DefaultBuildOperationQueueTest.SimpleWorker()

        when:
        async {
            start {
                def cl = registry.operationStart()
                instant.worker1
                thread.blockUntil.worker2Ready
                thread.block()
                instant.worker1Finished
                cl.operationFinish()
            }
            start {
                thread.blockUntil.worker1
                instant.worker2Ready
                def child2 = registry.operationStart()
                processor.run(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run() {
                            instant.worker2
                        }
                    })
                })
                child2.operationFinish()
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
        def processor = new DefaultBuildOperationProcessor(new TestBuildOperationExecutor(), new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new DefaultBuildOperationQueueTest.SimpleWorker()

        when:
        async {
            start {
                def cl = registry.operationStart()
                processor.run(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run() {
                            instant.worker1
                            thread.blockUntil.worker2Ready
                            thread.block()
                            instant.worker1Finished
                        }
                    })
                })
                cl.operationFinish()
            }
            start {
                thread.blockUntil.worker1
                instant.worker2Ready
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

    def "BuildOperationWorkerRegistry operations nested in BuildOperationProcessor operations borrow parent lease"() {
        given:
        def maxWorkers = 1
        def registry = new DefaultBuildOperationWorkerRegistry(maxWorkers)
        def processor = new DefaultBuildOperationProcessor(new TestBuildOperationExecutor(), new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new DefaultBuildOperationQueueTest.SimpleWorker()

        when:
        def outer = registry.operationStart()
        processor.run(processorWorker, { queue ->
            queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                @Override
                void run() {
                    instant.child1Started
                    thread.block()
                    instant.child1Finished
                }
            })
            queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                @Override
                void run() {
                    instant.child2Started
                    thread.block()
                    instant.child2Finished
                }
            })
        })
        outer.operationFinish()

        then:
        instant.child2Started > instant.child1Finished || instant.child1Started > instant.child2Finished

        cleanup:
        registry?.stop()
    }
}
