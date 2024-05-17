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

import org.gradle.internal.concurrent.DefaultWorkerLimits
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CountDownLatch

class MaxWorkersTest extends ConcurrentSpec {

    def "BuildOperationExecutor operation start blocks when there are no leases available, taken by BuildOperationWorkerRegistry"() {
        given:
        def maxWorkers = 1
        def workerLeaseService = this.workerLeaseService(maxWorkers)
        def processor = createProcessor(workerLeaseService, maxWorkers)
        def processorWorker = new SimpleWorker()

        when:
        async {
            start {
                def cl = workerLeaseService.startWorker()
                instant.worker1
                thread.blockUntil.worker2Ready
                thread.block()
                instant.worker1Finished
                cl.leaseFinish()
            }
            start {
                thread.blockUntil.worker1
                instant.worker2Ready
                ConcurrentSpec spec = this;
                def child2 = workerLeaseService.startWorker()
                processor.runAll(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run(BuildOperationContext buildOperationContext) {
                            spec.instant.worker2
                        }
                    })
                })
                child2.leaseFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        workerLeaseService?.stop()
    }

    private createProcessor(WorkerLeaseService workerLeaseService, int maxWorkers) {
        return BuildOperationExecutorSupport.builder(maxWorkers)
            .withWorkerLeaseService(workerLeaseService)
            .build()
    }

    def "BuildOperationWorkerRegistry operation start blocks when there are no leases available, taken by BuildOperationExecutor"() {
        given:
        def maxWorkers = 1
        def workerLeaseService = this.workerLeaseService(maxWorkers)
        def processor = createProcessor(workerLeaseService, maxWorkers)
        def processorWorker = new SimpleWorker()
        def spec = this
        when:
        async {
            start {
                def cl = workerLeaseService.startWorker()
                processor.runAll(processorWorker, { queue ->
                    queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                        @Override
                        void run(BuildOperationContext buildOperationContext) {
                            spec.instant.worker1
                            spec.thread.blockUntil.worker2Ready
                            spec.thread.block()
                            spec.instant.worker1Finished
                        }
                    })
                })
                cl.leaseFinish()
            }
            start {
                spec.thread.blockUntil.worker1
                spec.instant.worker2Ready
                def cl = workerLeaseService.startWorker()
                spec.instant.worker2
                cl.leaseFinish()
            }
        }

        then:
        instant.worker2 > instant.worker1Finished

        cleanup:
        workerLeaseService?.stop()
    }

    def "BuildOperationWorkerRegistry operations nested in BuildOperationExecutor operations borrow parent lease"() {
        given:
        def maxWorkers = 1
        def workerLeaseService = this.workerLeaseService(maxWorkers)
        def processor = createProcessor(workerLeaseService, maxWorkers)
        def processorWorker = new SimpleWorker()
        def spec = this
        when:
        async {
            def outer = workerLeaseService.startWorker()
            processor.runAll(processorWorker, { queue ->
                queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                    @Override
                    void run(BuildOperationContext buildOperationContext) {
                        spec.instant.child1Started
                        spec.thread.block()
                        spec.instant.child1Finished
                    }
                })
                queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                    @Override
                    void run(BuildOperationContext buildOperationContext) {
                        spec.instant.child2Started
                        spec.thread.block()
                        spec.instant.child2Finished
                    }
                })
            })
            outer.leaseFinish()
        }

        then:
        instant.child2Started > instant.child1Finished || instant.child1Started > instant.child2Finished

        cleanup:
        workerLeaseService?.stop()
    }

    def "BuildOperationExecutor can fully utilize worker leases when multiple threads owning worker leases are submitting work (maxWorkers: #maxWorkers)"() {
        CountDownLatch leaseAcquiredLatch = new CountDownLatch(maxWorkers)
        CountDownLatch runningLatch = new CountDownLatch(maxWorkers)
        CountDownLatch fullyUtilizedLatch = new CountDownLatch(1)
        final Object release = new Object()

        def workerLeaseService = this.workerLeaseService(maxWorkers)
        def processor = createProcessor(workerLeaseService, maxWorkers)
        def processorWorker = new SimpleWorker()

        expect:
        async {
            maxWorkers.times {
                start {
                    def lease = workerLeaseService.startWorker()
                    synchronized (release) {
                        leaseAcquiredLatch.countDown()
                        release.wait()
                    }

                    processor.runAll(processorWorker, { queue ->
                        maxWorkers.times {
                            queue.add(new DefaultBuildOperationQueueTest.TestBuildOperation() {
                                @Override
                                void run(BuildOperationContext buildOperationContext) {
                                    runningLatch.countDown()
                                    // block until all worker leases are utilized
                                    fullyUtilizedLatch.await()
                                }
                            })
                        }

                        // Release the next thread to submit its work
                        synchronized (release) {
                            release.notify()
                        }
                    })
                    lease.leaseFinish()
                }
            }

            leaseAcquiredLatch.await()

            // release the first queue to submit its work
            synchronized (release) {
                release.notify()
            }

            // Wait for all worker leases to be utilized running work
            runningLatch.await()
            fullyUtilizedLatch.countDown()
        }

        where:
        maxWorkers << [1, 2, 4]
    }

    WorkerLeaseService workerLeaseService(int maxWorkers) {
        def service = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), new DefaultWorkerLimits(maxWorkers))
        service.startProjectExecution(true)
        return service
    }

    static class SimpleWorker implements BuildOperationWorker<DefaultBuildOperationQueueTest.TestBuildOperation> {
        void execute(DefaultBuildOperationQueueTest.TestBuildOperation run, BuildOperationContext context) { run.run(context) }
    }
}
