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
import org.gradle.internal.progress.BuildOperationState
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.resources.ProjectLeaseRegistry
import org.gradle.internal.resources.ResourceLockCoordinationService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultAsyncWorkTrackerTest extends ConcurrentSpec {
    ResourceLockCoordinationService coordinationService = new DefaultResourceLockCoordinationService()
    WorkerLeaseService workerLeaseService = new DefaultWorkerLeaseService(coordinationService, true, 1)
    AsyncWorkTracker asyncWorkTracker = new DefaultAsyncWorkTracker(workerLeaseService)

    def "can wait for async work to complete"() {
        def operation = Mock(BuildOperationState)

        when:
        async {
            5.times { i ->
                start {
                    asyncWorkTracker.registerWork(operation, blockingWorkCompletion("allStarted"))
                    instant."worker${i}Started"
                }
            }
            5.times { i ->
                thread.blockUntil."worker${i}Started"
            }
            start {
                instant.waitStarted
                asyncWorkTracker.waitForCompletion(operation)
                instant.waitFinished
            }
            thread.blockUntil.waitStarted
            instant.allStarted
        }

        then:
        instant.waitFinished >= instant.allStarted
    }

    def "work in different operations does not affect each other"() {
        def operation1 = Mock(BuildOperationState)
        def operation2 = Mock(BuildOperationState)

        when:
        async {
            start {
                asyncWorkTracker.registerWork(operation1, blockingWorkCompletion("completeWorker1"))
                instant.worker1Started
            }
            start {
                asyncWorkTracker.registerWork(operation2, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        throw new IllegalStateException()
                    }

                    @Override
                    boolean isComplete() {
                        return false
                    }
                })
                instant.worker2Started
            }
            thread.blockUntil.worker1Started
            thread.blockUntil.worker2Started
            start {
                asyncWorkTracker.waitForCompletion(operation1)
                instant.waitFinished
            }
            instant.completeWorker1
        }

        then:
        instant.waitFinished >= instant.completeWorker1
    }

    def "work can be submitted to one operation while another operation is being waited on"() {
        def operation1 = Mock(BuildOperationState)
        def operation2 = Mock(BuildOperationState)

        when:
        async {
            start {
                asyncWorkTracker.registerWork(operation1, blockingWorkCompletion("completeWorker1"))
                instant.worker1Started
            }
            thread.blockUntil.worker1Started
            start {
                instant.waitStarted
                asyncWorkTracker.waitForCompletion(operation1)
                instant.waitFinished
            }
            start {
                thread.blockUntil.waitStarted
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
        def operation1 = Mock(BuildOperationState)

        when:
        async {
            start {
                asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                    @Override
                    void waitForCompletion() {
                        throw new RuntimeException("BOOM!")
                    }

                    @Override
                    boolean isComplete() {
                        return false
                    }
                })
                instant.worker1Started
            }
            start {
                asyncWorkTracker.registerWork(operation1, blockingWorkCompletion("completeWorker2"))
                instant.worker2Started
            }
            thread.blockUntil.worker1Started
            thread.blockUntil.worker2Started
            start {
                try {
                    asyncWorkTracker.waitForCompletion(operation1)
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

    def "an error is thrown when work is submitted while being waited on"() {
        def operation1 = Mock(BuildOperationState)

        when:
        async {
            start {
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
                })
                instant.registered
            }
            start {
                thread.blockUntil.registered
                asyncWorkTracker.waitForCompletion(operation1)
            }
            thread.blockUntil.waitStarted
            start {
                try {
                    asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
                        @Override
                        void waitForCompletion() {
                        }

                        @Override
                        boolean isComplete() {
                            return false
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

    def "releases a project lock before waiting on async work"() {
        def projectLockService = Mock(ProjectLeaseRegistry)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(projectLockService)
        def operation1 = Mock(BuildOperationState)

        when:
        asyncWorkTracker.registerWork(operation1, new AsyncWorkCompletion() {
            @Override
            void waitForCompletion() {
            }

            @Override
            boolean isComplete() {
                return false
            }
        })
        asyncWorkTracker.waitForCompletion(operation1)

        then:
        1 * projectLockService.withoutProjectLock(_)
    }

    def "does not release a project lock before waiting on async work when no work is registered"() {
        def projectLockService = Mock(ProjectLeaseRegistry)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(projectLockService)
        def operation1 = Mock(BuildOperationState)

        when:
        asyncWorkTracker.waitForCompletion(operation1)

        then:
        0 * projectLockService.withoutProjectLock(_)
    }

    def "does not release a project lock when all async work is already completed"() {
        def projectLockService = Mock(ProjectLeaseRegistry)
        def asyncWorkTracker = new DefaultAsyncWorkTracker(projectLockService)
        def operation1 = Mock(BuildOperationState)

        when:
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.registerWork(operation1, completedWorkCompletion())
        asyncWorkTracker.waitForCompletion(operation1)

        then:
        0 * projectLockService.withoutProjectLock(_)
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
        }
    }
}
