/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.progress

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.internal.concurrent.GradleThread
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.DefaultBuildOperationProcessor
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.resources.DefaultResourceLockCoordinationService
import org.gradle.internal.time.TimeProvider
import org.gradle.internal.work.DefaultWorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultBuildOperationExecutorTest extends ConcurrentSpec {
    def listener = Mock(BuildOperationListener)
    def timeProvider = Mock(TimeProvider)
    def progressLoggerFactory = Mock(ProgressLoggerFactory)
    def operationExecutor = new DefaultBuildOperationExecutor(listener, timeProvider, progressLoggerFactory)

    def "fires events when operation starts and finishes successfully"() {
        setup:
        GradleThread.setManaged()

        and:
        def action = Mock(Transformer)
        def progressLogger = Mock(ProgressLogger)
        def descriptor = "some-thing"
        def operationDetails = BuildOperationDetails.displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>").operationDescriptor(descriptor).build()
        def id

        when:
        def result = operationExecutor.run(operationDetails, action)

        then:
        result == "result"

        then:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            id = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.name == "<op>"
            assert operation.displayName == "<some-operation>"
            assert operation.operationDescriptor == descriptor
            assert start.startTime == 123L
        }

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.setDescription("<some-operation>")
        1 * progressLogger.setShortDescription("<some-op>")
        1 * progressLogger.started()

        then:
        1 * action.transform(_) >> "result"

        then:
        1 * progressLogger.completed()

        then:
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.parentId == null
            assert operation.id == id
            assert operation.name == "<op>"
            assert operation.displayName == "<some-operation>"
            assert operation.operationDescriptor == descriptor
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == null
        }

        cleanup:
        GradleThread.setUnmanaged()
    }

    def "fires events when operation starts and fails"() {
        setup:
        GradleThread.setManaged()

        and:
        def action = Mock(Transformer)
        def operationDetails = BuildOperationDetails.displayName("<some-operation>").progressDisplayName("<some-op>").build()
        def failure = new RuntimeException()
        def progressLogger = Mock(ProgressLogger)
        def id

        when:
        operationExecutor.run(operationDetails, action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        then:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id != null
            id = operation.id
            assert operation.parentId == null
            assert operation.displayName == "<some-operation>"
            assert start.startTime == 123L
        }

        then:
        1 * progressLoggerFactory.newOperation(_) >> progressLogger
        1 * progressLogger.setDescription("<some-operation>")
        1 * progressLogger.setShortDescription("<some-op>")
        1 * progressLogger.started()

        then:
        1 * action.transform(_) >> { throw failure }

        then:
        1 * progressLogger.completed()

        then:
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == id
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == failure
        }

        cleanup:
        GradleThread.setUnmanaged()
    }

    def "action can mark operation as failed without throwing an exception"() {
        setup:
        GradleThread.setManaged()

        and:
        def action = Mock(Transformer)
        def failure = new RuntimeException()

        when:
        operationExecutor.run("<some-operation>", action)

        then:
        1 * action.transform(_) >> { BuildOperationContext context -> context.failed(failure) }

        then:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert opResult.failure == failure
        }

        cleanup:
        GradleThread.setUnmanaged()
    }

    def "action can provide operation result"() {
        setup:
        GradleThread.setManaged()

        and:
        def action = Mock(Transformer)
        def result = "SomeResult"

        when:
        operationExecutor.run("<some-operation>", action)

        then:
        1 * action.transform(_) >> { BuildOperationContext context -> context.result = result }

        then:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert opResult.result == result
        }

        cleanup:
        GradleThread.setUnmanaged()
    }

    def "does not generate progress logging when operation has no progress display name"() {
        def action = Mock(Transformer)

        when:
        def result = operationExecutor.run("<some-operation>", action)

        then:
        result == "result"

        then:
        1 * action.transform(_) >> "result"
        0 * progressLoggerFactory._
    }

    def "multiple threads can run independent operations concurrently"() {
        def id1
        def id2

        when:
        async {
            start {
                GradleThread.setManaged()
                operationExecutor.run("<thread-1>", {
                    instant.action1Started
                    thread.blockUntil.action2Started
                } as Action)
            }
            thread.blockUntil.action1Started
            GradleThread.setManaged()
            operationExecutor.run("<thread-2>", {
                instant.action2Started
                thread.blockUntil.action1Finished
            } as Action)
        }

        then:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            id1 = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.displayName == "<thread-1>"
        }
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            id2 = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.displayName == "<thread-2>"
        }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == id1
            assert opResult.failure == null
            instant.action1Finished
        }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == id2
            assert opResult.failure == null
        }
    }

    def "multiple threads can run child operations concurrently"() {
        setup:
        GradleThread.setManaged()

        and:
        def parentId
        def id1
        def id2

        when:
        operationExecutor.run("<main>", {
            def parentOp = operationExecutor.currentOperation
            parentId = parentOp.id
            async {
                start {
                    def details = BuildOperationDetails.displayName("<thread-1>").parent(parentOp).build()
                    operationExecutor.run(details, {
                        instant.action1Started
                        thread.blockUntil.action2Started
                    } as Action)
                }
                start {
                    thread.blockUntil.action1Started
                    def details = BuildOperationDetails.displayName("<thread-2>").parent(parentOp).build()
                    operationExecutor.run(details, {
                        instant.action2Started
                        thread.blockUntil.action1Finished
                    } as Action)
                }
            }
        } as Action)

        then:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id != null
            assert operation.parentId == null
            assert operation.displayName == "<main>"
        }
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            id1 = operation.id
            assert operation.id != null
            assert operation.parentId == parentId
            assert operation.displayName == "<thread-1>"
        }
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            id2 = operation.id
            assert operation.id != null
            assert operation.parentId == parentId
            assert operation.displayName == "<thread-2>"
        }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == id1
            assert opResult.failure == null
            instant.action1Finished
        }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == id2
            assert opResult.failure == null
        }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == parentId
            assert opResult.failure == null
        }

        cleanup:
        GradleThread.setUnmanaged()
    }

    def "cannot start child operation when parent has completed"() {
        def operation

        given:
        operationExecutor.run("parent", {
            operation = operationExecutor.currentOperation
        } as Action)

        when:
        operationExecutor.run(BuildOperationDetails.displayName("child").parent(operation).build(), {} as Action)

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot start operation (child) as parent operation (parent) has already completed.'
    }

    def "child fails when parent completes while child is still running"() {
        when:
        async {
            operationExecutor.run("parent", {
                def operation = operationExecutor.currentOperation
                start {
                    operationExecutor.run(BuildOperationDetails.displayName("child").parent(operation).build(), {
                        instant.childStarted
                        thread.blockUntil.parentCompleted
                    } as Action)
                }
                thread.blockUntil.childStarted
            } as Action)
            instant.parentCompleted
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Parent operation (parent) completed before this operation (child).'
    }

    def "can query operation id from inside operation"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)
        def id

        when:
        operationExecutor.run("<parent>", action1)

        then:
        1 * action1.execute(_) >> {
            assert operationExecutor.currentOperation.id != null
            id = operationExecutor.currentOperation.id
            operationExecutor.run("<child>", action2)
        }
        1 * action2.execute(_) >> {
            assert operationExecutor.currentOperation.id != null
            assert operationExecutor.currentOperation.id != id
        }
    }

    def "cannot query operation id when no operation running on current managed thread"() {
        when:
        async {
            start {
                operationExecutor.run("operation", {
                    instant.operationRunning
                    thread.blockUntil.queried
                } as Action)
            }
            thread.blockUntil.operationRunning
            GradleThread.setManaged()
            try {
                operationExecutor.currentOperation
            } finally {
                instant.queried
                GradleThread.setUnmanaged()
            }
        }

        then:
        IllegalStateException e = thrown()
        e.message == "No operation is currently running."
    }

    def "cannot query current operation when no operation running on current unmanaged thread"() {
        when:
        BuildOperationExecutor.Operation op
        async {
            assert !GradleThread.managed
            op = operationExecutor.currentOperation
        }

        then:
        def ex = thrown(IllegalStateException)
        ex.message == 'No operation is currently running.'
    }


    def "can nest operations on unmanaged threads"() {
        when:
        async {
            assert !GradleThread.managed
            operationExecutor.run('outer', {
                assert operationExecutor.currentOperation instanceof DefaultBuildOperationExecutor.OperationDetails
                assert operationExecutor.currentOperation.parentId.id < 0
                operationExecutor.run('inner', {} as Action)
            } as Action)
        }

        then:
        noExceptionThrown()
    }

    def "attaches parent id when operation is nested inside another"() {
        setup:
        GradleThread.setManaged()

        and:
        def action1 = Mock(Transformer)
        def action2 = Mock(Transformer)
        def action3 = Mock(Transformer)
        def parentId
        def child1Id
        def child2Id

        when:
        def result = operationExecutor.run("<parent>", action1)

        then:
        result == "result"

        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id != null
            parentId = operation.id
            assert operation.parentId == null
        }
        1 * action1.transform(_) >> {
            return operationExecutor.run("<op-2>", action2)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            child1Id = operation.id
            assert operation.parentId == parentId
        }
        1 * action2.transform(_) >> {
            return operationExecutor.run("<op-3>", action3)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            child2Id = operation.id
            assert operation.parentId == child1Id
        }
        1 * action3.transform(_) >> {
            return "result"
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == child2Id
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == child1Id
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == parentId
        }

        cleanup:
        GradleThread.setUnmanaged()
    }

    def "attaches correct parent id when multiple threads run nested operations"() {
        def parent1Id
        def parent2Id
        def child1Id
        def child2Id

        when:
        async {
            start {
                GradleThread.setManaged()
                operationExecutor.run("<parent-1>", {
                    operationExecutor.run("<child-1>", {
                        instant.child1Started
                        thread.blockUntil.child2Started
                    } as Action)
                } as Action)
            }
            start {
                GradleThread.setManaged()
                operationExecutor.run("<parent-2>", {
                    operationExecutor.run("<child-2>", {
                        instant.child2Started
                        thread.blockUntil.child1Started
                    } as Action)
                } as Action)
            }
        }

        then:
        1 * listener.started({it.displayName == "<parent-1>" }, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id != null
            parent1Id = operation.id
            assert operation.parentId == null
        }
        1 * listener.started({it.displayName == "<parent-2>" }, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id != null
            parent2Id = operation.id
            assert operation.parentId == null
        }
        1 * listener.started({it.displayName == "<child-1>" }, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id != null
            child1Id = operation.id
            assert operation.parentId == parent1Id
        }
        1 * listener.started({it.displayName == "<child-2>" }, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id != null
            child2Id = operation.id
            assert operation.parentId == parent2Id
        }

        and:
        1 * listener.finished({ it.id == child1Id }, _)
        1 * listener.finished({ it.id == child2Id }, _)
        1 * listener.finished({ it.id == parent1Id }, _)
        1 * listener.finished({ it.id == parent2Id }, _)
    }

    def "attaches parent id when sibling operation fails"() {
        setup:
        GradleThread.setManaged()

        and:
        def action1 = Mock(Transformer)
        def action2 = Mock(Transformer)
        def action3 = Mock(Transformer)
        def parentId
        def child1Id
        def child2Id

        when:
        def result = operationExecutor.run("<parent>", action1)

        then:
        result == "result"

        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            parentId = operation.id
            assert operation.parentId == null
        }
        1 * action1.transform(_) >> {
            try {
                operationExecutor.run("<child-1>", action2)
            } catch (RuntimeException) {
                // Ignore
            }
            return operationExecutor.run("<child-2>", action3)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            child1Id = operation.id
            assert operation.parentId == parentId
        }
        1 * action2.transform(_) >> { throw new RuntimeException() }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == child1Id
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            child2Id = operation.id
            assert operation.parentId == parentId
        }
        1 * action3.transform(_) >> {
            return "result"
        }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == child2Id
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == parentId
        }

        cleanup:
        GradleThread.setUnmanaged()
    }

    def "can be used through BuildOperationProcessor on unmanaged threads"() {
        given:
        def maxWorkers = 2
        def workerLeases = new DefaultWorkerLeaseService(new DefaultResourceLockCoordinationService(), true, maxWorkers)
        def processor = new DefaultBuildOperationProcessor(operationExecutor, new DefaultBuildOperationQueueFactory(workerLeases), executorFactory, maxWorkers)
        def operation = Mock(RunnableBuildOperation)

        when:
        async {
            processor.run({ queue ->
                5.times { queue.add(operation) }
            })
        }

        then:
        5 * operation.run()

        cleanup:
        workerLeases?.stop()
    }
}
