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
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.progress.BuildOperationListener
import org.gradle.internal.progress.DefaultBuildOperationExecutor
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.work.WorkerLeaseRegistry
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class MaxWorkersTest extends ConcurrentSpec {

    def "BuildOperationExecutor operation start blocks when there are no leases available, taken by BuildOperationWorkerRegistry"() {
        given:
        def maxWorkers = 1
        def registry = buildOperationWorkerRegistry(maxWorkers)
        def processor = new DefaultBuildOperationExecutor(
            Mock(BuildOperationListener), Mock(TimeProvider), Mock(ProgressLoggerFactory),
            new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new SimpleWorker()

        when:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                instant.worker1
                thread.blockUntil.worker2Ready
                thread.block()
                instant.worker1Finished
                cl.leaseFinish()
            }
            start {
                thread.blockUntil.worker1
                instant.worker2Ready
                def child2 = registry.getWorkerLease().start()
                processor.runAll(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run(BuildOperationContext buildOperationContext) {
                            instant.worker2
                        }
                    })
                })
                child2.leaseFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "BuildOperationWorkerRegistry operation start blocks when there are no leases available, taken by BuildOperationExecutor"() {
        given:
        def maxWorkers = 1
        def registry = buildOperationWorkerRegistry(maxWorkers)
        def processor = new DefaultBuildOperationExecutor(
            Mock(BuildOperationListener), Mock(TimeProvider), Mock(ProgressLoggerFactory),
            new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new SimpleWorker()

        when:
        async {
            start {
                def cl = registry.getWorkerLease().start()
                processor.runAll(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run(BuildOperationContext buildOperationContext) {
                            instant.worker1
                            thread.blockUntil.worker2Ready
                            thread.block()
                            instant.worker1Finished
                        }
                    })
                })
                cl.leaseFinish()
            }
            start {
                thread.blockUntil.worker1
                instant.worker2Ready
                def cl = registry.getWorkerLease().start()
                instant.worker2
                cl.leaseFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        registry?.stop()
    }

    def "BuildOperationWorkerRegistry operations nested in BuildOperationExecutor operations borrow parent lease"() {
        given:
        def maxWorkers = 1
        def registry = buildOperationWorkerRegistry(maxWorkers)
        def processor = new DefaultBuildOperationExecutor(
            Mock(BuildOperationListener), Mock(TimeProvider), Mock(ProgressLoggerFactory),
            new DefaultBuildOperationQueueFactory(registry), new DefaultExecutorFactory(), maxWorkers)
        def processorWorker = new SimpleWorker()

        when:
        def outer = registry.getWorkerLease().start()
        processor.runAll(processorWorker, { queue ->
            queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                @Override
                void run(BuildOperationContext buildOperationContext) {
                    instant.child1Started
                    thread.block()
                    instant.child1Finished
                }
            })
            queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                @Override
                void run(BuildOperationContext buildOperationContext) {
                    instant.child2Started
                    thread.block()
                    instant.child2Finished
                }
            })
        })
        outer.leaseFinish()

        then:
        instant.child2Started > instant.child1Finished || instant.child1Started > instant.child2Finished

        cleanup:
        registry?.stop()
    }

    WorkerLeaseRegistry buildOperationWorkerRegistry(int maxWorkers) {
        return new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), true, maxWorkers)
    }

    static class SimpleWorker implements BuildOperationWorker<DefaultBuildOperationQueueTest.TestBuildOperation> {
        void execute(DefaultBuildOperationQueueTest.TestBuildOperation run, BuildOperationContext context) { run.run(context) }
        String getDisplayName() { return getClass().simpleName }
    }
}
