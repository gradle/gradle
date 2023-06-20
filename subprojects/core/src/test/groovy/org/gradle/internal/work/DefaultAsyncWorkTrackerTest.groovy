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

package org.gradle.internal.work


import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.operations.BuildOperationRef
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.work.TestWorkerLeaseService

import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_AND_REACQUIRE_PROJECT_LOCKS
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RELEASE_PROJECT_LOCKS
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RETAIN_PROJECT_LOCKS

class DefaultAsyncWorkTrackerTest extends ConcurrentSpec {
    WorkerLeaseService workerLeaseService = new TestWorkerLeaseService()
    AsyncWorkTracker asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)

    def "can wait for async work to complete"() {
        def operation = Mock(BuildOperationRef)

        when:
        async {
            5.times { i ->
                workerThread {
                    asyncWorkTracker.registerWork(operation, blockingWorkCompletion("allStarted"))
                    instant."worker${i}Started"
                }
            }
            5.times { i ->
                thread.blockUntil."worker${i}Started"
            }
            workerThread {
                instant.waitStarted
                asyncWorkTracker.waitForCompletion(operation, RELEASE_PROJECT_LOCKS)
                instant.waitFinished
            }
            thread.blockUntil.waitStarted
            instant.allStarted
        }

        then:
        instant.waitFinished >= instant.allStarted
    }

    def "work in different operations does not affect each other"() {
        def operation1 = Mock(BuildOperationRef)
        def operation2 = Mock(BuildOperationRef)

        when:
        async {
            workerThread {
                asyncWorkTracker.registerWork(operation1, blockingWorkCompletion("completeWorker1"))
                instant.worker1Started
            }
            workerThread {
                asyncWorkTracker.registerWork(operation2, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        throw new IllegalStateException()
                    }

                    @Override
                    boolean isComplete() {
                        return false
                    }

                    @Override
                    void cancel() {

                    }
                })
                instant.worker2Started
            }
            thread.blockUntil.worker1Started
            thread.blockUntil.worker2Started
            workerThread {
                asyncWorkTracker.waitForCompletion(operation1, RELEASE_PROJECT_LOCKS)
                instant.waitFinished
            }
            instant.completeWorker1
        }

        then:
        instant.waitFinished >= instant.completeWorker1
    }

    def "work can be submitted to one operation while another operation is being waited on"() {
        def operation1 = Mock(BuildOperationRef)
        def operation2 = Mock(BuildOperationRef)

        when:
        async {
            workerThread {
                asyncWorkTracker.registerWork(operation1, blockingWorkCompletion("completeWorker1"))
                instant.worker1Started
            }
            thread.blockUntil.worker1Started
            workerThread {
                instant.waitStarted
                asyncWorkTracker.waitForCompletion(operation1, RELEASE_PROJECT_LOCKS)
                instant.waitFinished
            }
            thread.blockUntil.waitStarted
            workerThread {
                asyncWorkTracker.registerWork(operation2, blockingWorkCompletion("completeWorker1"))
                instant.worker2Started
            }
            thread.blockUntil.worker2Started
            instant.completeWorker1
        }

        then:
        instant.waitFinished >= instant.worker2Started
    }

    def "can wait for failing work to complete"() {
        def operation1 = Mock(BuildOperationRef)

        when:
        async {
            workerThread {
                asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        throw new RuntimeException("BOOM!")
                    }

                    @Override
                    boolean isComplete() {
                        return false
                    }

                    @Override
                    void cancel() {

                    }
                })
                instant.worker1Started
            }
            workerThread {
                asyncWorkTracker.registerWork(operation1, blockingWorkCompletion("completeWorker2"))
                instant.worker2Started
            }
            thread.blockUntil.worker1Started
            thread.blockUntil.worker2Started
            workerThread {
                try {
                    asyncWorkTracker.waitForCompletion(operation1, RELEASE_PROJECT_LOCKS)
                } finally {
                    instant.waitFinished
                }
            }
            instant.completeWorker2
            thread.blockUntil.waitFinished
        }

        then:
        def e = thrown(DefaultMultiCauseException)
        e.causes.size() == 1

        and:
        e.causes.get(0).message == "BOOM!"

        and:
        instant.waitFinished >= instant.completeWorker2
    }

    def "can capture failures from work that is already complete"() {
        def operation1 = Mock(BuildOperationRef)

        given:
        asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
            @Override
            void waitForCompletion() {
                throw new RuntimeException("BOOM!")
            }

            @Override
            boolean isComplete() {
                return true
            }

            @Override
            void cancel() {

            }
        })
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())

        when:
        workerLeaseService.runAsWorkerThread {
            asyncWorkTracker.waitForCompletion(operation1, RELEASE_PROJECT_LOCKS)
        }

        then:
        def e = thrown(DefaultMultiCauseException)
        e.causes.size() == 1

        and:
        e.causes.get(0).message == "BOOM!"
    }

    def "an error is thrown when work is submitted while being waited on"() {
        def operation1 = Mock(BuildOperationRef)

        when:
        async {
            workerThread {
                asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        instant.waitStarted
                        thread.blockUntil.completeWait
                    }

                    @Override
                    boolean isComplete() {
                        return false
                    }

                    @Override
                    void cancel() {

                    }
                })
                instant.registered
            }
            thread.blockUntil.registered
            workerThread {
                asyncWorkTracker.waitForCompletion(operation1, RELEASE_PROJECT_LOCKS)
            }
            thread.blockUntil.waitStarted
            workerThread {
                try {
                    asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                        @Override
                        void waitForCompletion() {
                        }

                        @Override
                        boolean isComplete() {
                            return false
                        }

                        @Override
                        void cancel() {

                        }
                    })
                } finally {
                    instant.failure
                }
            }
            thread.blockUntil.failure
            instant.completeWait
        }

        then:
        def e = thrown(IllegalStateException)

        and:
        e.message == "Another thread is currently waiting on the completion of work for the provided operation"
    }

    def "can temporarily release a project lock while waiting on async work"() {
        def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
        def workerLeaseService = Mock(WorkerLeaseService)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)
        def operation1 = Mock(BuildOperationRef)

        when:
        asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
            @Override
            void waitForCompletion() {
            }

            @Override
            boolean isComplete() {
                return false
            }

            @Override
            void cancel() {

            }
        })
        asyncWorkTracker.waitForCompletion(operation1, RELEASE_AND_REACQUIRE_PROJECT_LOCKS)

        then:
        _ * workerLeaseService.currentWorkerLease >> workerLease
        1 * workerLeaseService.runAsIsolatedTask(_) >> { Runnable runnable -> runnable.run() }
        1 * workerLeaseService.withoutLock(workerLease, _) >> { locks, Runnable runnable -> runnable.run() }
        0 * workerLeaseService._
    }

    def "can release a project lock before waiting on async work"() {
        def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
        def workerLeaseService = Mock(WorkerLeaseService)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)
        def operation1 = Mock(BuildOperationRef)

        when:
        asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
            @Override
            void waitForCompletion() {
            }

            @Override
            boolean isComplete() {
                return false
            }

            @Override
            void cancel() {

            }
        })
        asyncWorkTracker.waitForCompletion(operation1, RELEASE_PROJECT_LOCKS)

        then:
        _ * workerLeaseService.currentWorkerLease >> workerLease
        1 * workerLeaseService.runAsIsolatedTask()
        1 * workerLeaseService.withoutLock(workerLease, _) >> { locks, Runnable runnable -> runnable.run() }
        0 * workerLeaseService._
    }

    def "does not release a project lock before waiting on async work when locks are retained"() {
        def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
        def workerLeaseService = Mock(WorkerLeaseService)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)
        def operation1 = Mock(BuildOperationRef)

        when:
        asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
            @Override
            void waitForCompletion() {
            }

            @Override
            boolean isComplete() {
                return false
            }

            @Override
            void cancel() {

            }
        })
        asyncWorkTracker.waitForCompletion(operation1, RETAIN_PROJECT_LOCKS)

        then:
        _ * workerLeaseService.currentWorkerLease >> workerLease
        1 * workerLeaseService.withoutLock(workerLease, _) >> { locks, Runnable runnable -> runnable.run() }
        0 * workerLeaseService._
    }

    def "does not temporarily release a project lock before waiting on async work when no work is registered"() {
        def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
        def workerLeaseService = Mock(WorkerLeaseService)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)
        def operation1 = Mock(BuildOperationRef)

        when:
        asyncWorkTracker.waitForCompletion(operation1, RELEASE_AND_REACQUIRE_PROJECT_LOCKS)

        then:
        0 * workerLeaseService._
    }

    def "does not temporarily release a project lock when all async work is already completed"() {
        def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
        def workerLeaseService = Mock(WorkerLeaseService)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)
        def operation1 = Mock(BuildOperationRef)

        when:
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.waitForCompletion(operation1, RELEASE_AND_REACQUIRE_PROJECT_LOCKS)

        then:
        _ * workerLeaseService.currentWorkerLease >> workerLease
        1 * workerLeaseService.withoutLock(workerLease, _) >> { locks, Runnable runnable -> runnable.run() }
        0 * workerLeaseService._
    }

    def "can release a project lock when all async work is already completed"() {
        def workerLease = Mock(WorkerLeaseRegistry.WorkerLease)
        def workerLeaseService = Mock(WorkerLeaseService)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)
        def operation1 = Mock(BuildOperationRef)

        when:
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.waitForCompletion(operation1, RELEASE_PROJECT_LOCKS)

        then:
        _ * workerLeaseService.currentWorkerLease >> workerLease
        1 * workerLeaseService.runAsIsolatedTask()
        1 * workerLeaseService.withoutLock(workerLease, _) >> { locks, Runnable runnable -> runnable.run() }
        0 * workerLeaseService._
    }

    void workerThread(Closure cl) {
        start {
            workerLeaseService.runAsWorkerThread(cl)
        }
    }

    AsyncWorkCompletion blockingWorkCompletion(String instant) {
        return new AsyncWorkCompletion() {
            @Override
            void waitForCompletion() {
                thread.blockUntil."${instant}"
            }

            @Override
            boolean isComplete() {
                return false
            }

            @Override
            void cancel() {

            }
        }
    }

    AsyncWorkCompletion completedWorkCompletion() {
        new AsyncWorkCompletion() {
            @Override
            void waitForCompletion() {
            }

            @Override
            boolean isComplete() {
                return true
            }

            @Override
            void cancel() {

            }
        }
    }
}
