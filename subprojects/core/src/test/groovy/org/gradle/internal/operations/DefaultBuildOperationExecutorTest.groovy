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

package org.gradle.internal.operations


import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.internal.time.Clock
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import static org.gradle.internal.operations.BuildOperationDescriptor.displayName

class DefaultBuildOperationExecutorTest extends ConcurrentSpec {
    def listener = Mock(BuildOperationListener)
    def timeProvider = Mock(Clock)
    def progressLoggerFactory = Spy(NoOpProgressLoggerFactory)
    def operationExecutor = new DefaultBuildOperationExecutor(listener, timeProvider, progressLoggerFactory, Mock(BuildOperationQueueFactory), Mock(ExecutorFactory), new DefaultParallelismConfiguration(true, 1), new DefaultBuildOperationIdFactory())

    def setup() {
        CurrentBuildOperationRef.instance().clear()
    }

    def "fires events when wrap-around operation starts and finishes successfully"() {
        setup:
        def buildOperation = Mock(CallableBuildOperation)
        def progressLogger = Spy(NoOpProgressLoggerFactory.Logger)
        def details = Mock(Object)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>").details(details)
        def id

        when:
        def result = operationExecutor.call(buildOperation)

        then:
        result == "result"

        then:
        1 * buildOperation.description() >> operationDetailsBuilder
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            id = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.name == "<op>"
            assert operation.displayName == "<some-operation>"
            assert operation.details == details
            assert start.startTime == 123L
        }

        then:
        1 * progressLoggerFactory.newOperation(_ as Class, _ as BuildOperationDescriptor) >> progressLogger
        1 * progressLogger.start("<some-operation>", "<some-op>")

        then:
        1 * buildOperation.call(_) >> "result"

        then:
        1 * progressLogger.completed(null, false)

        then:
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.parentId == null
            assert operation.id == id
            assert operation.name == "<op>"
            assert operation.displayName == "<some-operation>"
            assert operation.details == details
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == null
        }
    }

    def "fires events when non-wrap-around operation starts and finishes successfully"() {
        setup:
        def progressLogger = Spy(NoOpProgressLoggerFactory.Logger)
        def details = Mock(Object)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>").details(details)
        def id

        when:
        def handle = operationExecutor.start(operationDetailsBuilder)

        then:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            id = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.name == "<op>"
            assert operation.displayName == "<some-operation>"
            assert operation.details == details
            assert start.startTime == 123L
        }

        then:
        1 * progressLoggerFactory.newOperation(_ as Class, _ as BuildOperationDescriptor) >> progressLogger
        1 * progressLogger.start("<some-operation>", "<some-op>")

        when:
        handle.setResult("result")

        then:
        1 * progressLogger.completed(null, false)

        then:
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.parentId == null
            assert operation.id == id
            assert operation.name == "<op>"
            assert operation.displayName == "<some-operation>"
            assert operation.details == details
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == null
        }
    }

    def "fires events when wrap-around operation starts and fails"() {
        setup:
        def buildOperation = Mock(RunnableBuildOperation)
        def operationDescriptionBuilder = displayName("<some-operation>").progressDisplayName("<some-op>")
        def failure = new RuntimeException()
        def progressLogger = Spy(NoOpProgressLoggerFactory.Logger)
        def id

        when:
        operationExecutor.run(buildOperation)

        then:
        def e = thrown(RuntimeException)
        e == failure

        then:
        1 * buildOperation.description() >> operationDescriptionBuilder
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            assert operation.id != null
            id = operation.id
            assert operation.parentId == null
            assert operation.displayName == "<some-operation>"
            assert start.startTime == 123L
        }

        then:
        1 * progressLoggerFactory.newOperation(_ as Class, _ as BuildOperationDescriptor) >> progressLogger
        1 * progressLogger.start("<some-operation>", "<some-op>")

        then:
        1 * buildOperation.run(_) >> { throw failure }

        then:
        1 * progressLogger.completed(null, true)

        then:
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == id
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == failure
        }
    }

    def "fires events when non-wrap-around operation starts and fails"() {
        setup:
        def operationDescriptionBuilder = displayName("<some-operation>").progressDisplayName("<some-op>")
        def failure = new RuntimeException()
        def progressLogger = Spy(NoOpProgressLoggerFactory.Logger)
        def id

        when:
        def handle = operationExecutor.start(operationDescriptionBuilder)

        then:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            assert operation.id != null
            id = operation.id
            assert operation.parentId == null
            assert operation.displayName == "<some-operation>"
            assert start.startTime == 123L
        }

        then:
        1 * progressLoggerFactory.newOperation(_ as Class, _ as BuildOperationDescriptor) >> progressLogger
        1 * progressLogger.start("<some-operation>", "<some-op>")

        when:
        handle.failed(failure)

        then:
        1 * progressLogger.completed(null, true)

        then:
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == id
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == failure
        }
    }

    def "action can mark operation as failed without throwing an exception"() {
        setup:
        def buildOperation = Spy(TestRunnableBuildOperation)
        def failure = new RuntimeException()

        when:
        operationExecutor.run(buildOperation)

        then:
        1 * progressLoggerFactory.newOperation(_ as Class, _ as BuildOperationDescriptor) >> Spy(NoOpProgressLoggerFactory.Logger)
        1 * buildOperation.run(_) >> { BuildOperationContext context -> context.failed(failure) }

        then:
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert opResult.failure == failure
        }
    }

    def "action can provide operation result"() {
        setup:
        def buildOperation = Spy(TestRunnableBuildOperation)
        def result = "SomeResult"

        when:
        operationExecutor.run(buildOperation)

        then:
        1 * buildOperation.run(_) >> { BuildOperationContext context -> context.result = result }

        then:
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert opResult.result == result
        }
    }

    def "action can provide progress updates as status string or items completed"() {
        setup:
        def buildOperation = Mock(RunnableBuildOperation)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>")
        def progressLogger = Spy(NoOpProgressLoggerFactory.Logger)
        def progressLogger2 = Spy(NoOpProgressLoggerFactory.Logger)

        when:
        operationExecutor.run(buildOperation)

        then:
        1 * buildOperation.description() >> operationDetailsBuilder
        1 * progressLoggerFactory.newOperation(_ as Class, _ as BuildOperationDescriptor) >> progressLogger
        1 * progressLogger.start("<some-operation>", "<some-op>")

        then:
        1 * buildOperation.run(_) >> { BuildOperationContext context ->
            context.progress("progress 1")
            context.progress("progress 2")
            context.progress(2, 4, "gold pieces", "progress 3")
        }

        1 * progressLoggerFactory.newOperation(_ as Class, progressLogger) >> progressLogger2

        then:
        1 * progressLogger2.start("<some-operation>", "progress 1")

        then:
        1 * progressLogger2.progress("progress 2")

        then:
        1 * progressLogger2.progress("progress 3")
        1 * listener.progress(_, _) >> { OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent ->
            assert progressEvent.details instanceof OperationProgressDetails
            assert progressEvent.details.progress == 2
            assert progressEvent.details.total == 4
            assert progressEvent.details.units == "gold pieces"
        }

        then:
        1 * progressLogger2.completed()

        then:
        1 * progressLogger.completed(null, false)
    }

    def "multiple threads can run independent operations concurrently"() {
        def id1
        def id2

        when:
        async {
            start {
                operationExecutor.run(runnableBuildOperation("<thread-1>") {
                    instant.action1Started
                    thread.blockUntil.action2Started
                })
            }
            thread.blockUntil.action1Started
            operationExecutor.run(runnableBuildOperation("<thread-2>") {
                instant.action2Started
                thread.blockUntil.action1Finished
            })
        }

        then:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            id1 = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.displayName == "<thread-1>"
        }
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            id2 = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.displayName == "<thread-2>"
        }
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == id1
            assert opResult.failure == null
            instant.action1Finished
        }
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == id2
            assert opResult.failure == null
        }
    }

    def "multiple threads can run child operations concurrently"() {
        setup:
        BuildOperationRef parent
        def id1
        def id2

        when:
        operationExecutor.run(runnableBuildOperation("<main>") {
            parent = operationExecutor.currentOperation
            async {
                start {
                    operationExecutor.run(new RunnableBuildOperation() {
                        void run(BuildOperationContext context) {
                            instant.action1Started
                            thread.blockUntil.action2Started
                        }

                        BuildOperationDescriptor.Builder description() {
                            displayName("<thread-1>").parent(parent)
                        }
                    })
                }
                start {
                    thread.blockUntil.action1Started
                    operationExecutor.run(new RunnableBuildOperation() {
                        void run(BuildOperationContext context) {
                            instant.action2Started
                            thread.blockUntil.action1Finished
                        }

                        BuildOperationDescriptor.Builder description() {
                            displayName("<thread-2>").parent(parent)
                        }
                    })
                }
            }
        })

        then:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            assert operation.id != null
            assert operation.parentId == null
            assert operation.displayName == "<main>"
        }
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            id1 = operation.id
            assert operation.id != null
            assert operation.parentId == parent.id
            assert operation.displayName == "<thread-1>"
        }
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            id2 = operation.id
            assert operation.id != null
            assert operation.parentId == parent.id
            assert operation.displayName == "<thread-2>"
        }
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == id1
            assert opResult.failure == null
            instant.action1Finished
        }
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == id2
            assert opResult.failure == null
        }
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == parent.id
            assert opResult.failure == null
        }
    }

    def "cannot start child operation when parent has completed"() {
        BuildOperationRef parent = null

        given:
        operationExecutor.run(runnableBuildOperation("parent") {
            parent = operationExecutor.currentOperation
        })

        when:
        operationExecutor.run(new RunnableBuildOperation() {
            void run(BuildOperationContext context) {}

            BuildOperationDescriptor.Builder description() {
                displayName("child").parent(parent)
            }
        })

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot start operation (child) as parent operation (parent) has already completed.'
    }

    def "child fails when parent completes while child is still running"() {
        when:
        async {
            operationExecutor.run(runnableBuildOperation("parent") {
                def operation = operationExecutor.currentOperation
                start {
                    operationExecutor.run(new RunnableBuildOperation() {
                        void run(BuildOperationContext context) {
                            instant.childStarted
                            thread.blockUntil.parentCompleted
                        }

                        BuildOperationDescriptor.Builder description() {
                            displayName("child").parent(operation)
                        }
                    })
                }
                thread.blockUntil.childStarted
            })
            instant.parentCompleted
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Parent operation (parent) completed before this operation (child).'
    }

    def "fails when non-wrap-around operation finishes after parent"() {
        BuildOperationContext operationContext
        operationExecutor.run(runnableBuildOperation("parent") {
            operationContext = operationExecutor.start(displayName("child"))
        })

        when:
        operationContext.result = "result"

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Parent operation (parent) completed before this operation (child).'
    }

    def "can query operation id from inside operation"() {
        given:
        def parentOperation = Spy(TestRunnableBuildOperation)
        def childOperation = Spy(TestRunnableBuildOperation)
        def id

        when:
        operationExecutor.run(parentOperation)

        then:
        1 * parentOperation.run(_) >> {
            assert operationExecutor.currentOperation.id != null
            assert operationExecutor.currentOperation.parentId == null
            id = operationExecutor.currentOperation.id
            operationExecutor.run(childOperation)
        }
        1 * childOperation.run(_) >> {
            assert operationExecutor.currentOperation.id != null
            assert operationExecutor.currentOperation.id != id
            assert operationExecutor.currentOperation.parentId == id
        }
    }

    def "cannot query operation id when no operation running on current managed thread"() {
        when:
        async {
            start {
                operationExecutor.run(runnableBuildOperation("operation") {
                    instant.operationRunning
                    thread.blockUntil.queried
                })
            }
            thread.blockUntil.operationRunning
            try {
                operationExecutor.currentOperation.id
            } finally {
                instant.queried
            }
        }

        then:
        IllegalStateException e = thrown()
        e.message == "No operation is currently running."
    }

    def "cannot query current operation id when no operation running on current unmanaged thread"() {
        when:
        BuildOperationDescriptor op
        async {
            op = operationExecutor.currentOperation.id
        }

        then:
        def ex = thrown(IllegalStateException)
        ex.message == 'No operation is currently running.'
    }

    def "can nest operations on unmanaged threads"() {
        when:
        async {
            operationExecutor.run(new RunnableBuildOperation() {
                void run(BuildOperationContext outerContext) {
                    assert operationExecutor.currentOperation.id != null
                    assert operationExecutor.currentOperation.parentId == null

                    operationExecutor.run(new RunnableBuildOperation() {
                        void run(BuildOperationContext innerContext) {}

                        BuildOperationDescriptor.Builder description() { displayName('inner') }
                    })
                }

                BuildOperationDescriptor.Builder description() { displayName('outer') }
            })
        }

        then:
        noExceptionThrown()
    }

    def "attaches parent id when operation is nested inside another"() {
        setup:
        def operation1 = Spy(TestRunnableBuildOperation)
        def operation2 = Spy(TestRunnableBuildOperation)
        def operation3 = Spy(TestRunnableBuildOperation)
        def parentId
        def child1Id
        def child2Id

        when:
        operationExecutor.run(operation1)

        then:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            assert operation.id != null
            parentId = operation.id
            assert operation.parentId == null
        }
        1 * operation1.run(_) >> {
            operationExecutor.run(operation2)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            child1Id = operation.id
            assert operation.parentId == parentId
        }
        1 * operation2.run(_) >> {
            operationExecutor.run(operation3)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            child2Id = operation.id
            assert operation.parentId == child1Id
        }
        1 * operation3.run(_) >> {}

        and:
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == child2Id
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == child1Id
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == parentId
        }
    }

    def "attaches correct parent id when multiple threads run nested operations"() {
        def parent1Id
        def parent2Id
        def child1Id
        def child2Id

        when:
        async {
            start {
                operationExecutor.run(runnableBuildOperation("<parent-1>") {
                    operationExecutor.run(runnableBuildOperation("<child-1>") {
                        instant.child1Started
                        thread.blockUntil.child2Started
                    })
                })
            }
            start {
                operationExecutor.run(runnableBuildOperation("<parent-2>") {
                    operationExecutor.run(runnableBuildOperation("<child-2>") {
                        instant.child2Started
                        thread.blockUntil.child1Started
                    })
                })
            }
        }

        then:
        1 * listener.started({ it.displayName == "<parent-1>" }, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            assert operation.id != null
            parent1Id = operation.id
            assert operation.parentId == null
        }
        1 * listener.started({ it.displayName == "<parent-2>" }, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            assert operation.id != null
            parent2Id = operation.id
            assert operation.parentId == null
        }
        1 * listener.started({ it.displayName == "<child-1>" }, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            assert operation.id != null
            child1Id = operation.id
            assert operation.parentId == parent1Id
        }
        1 * listener.started({ it.displayName == "<child-2>" }, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
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
        def operation1 = Spy(TestRunnableBuildOperation)
        def operation2 = Spy(TestRunnableBuildOperation)
        def operation3 = Spy(TestRunnableBuildOperation)
        def parentId
        def child1Id
        def child2Id

        when:
        operationExecutor.run(operation1)

        then:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            parentId = operation.id
            assert operation.parentId == null
        }
        1 * operation1.run(_) >> {
            try {
                operationExecutor.run(operation2)
            } catch (RuntimeException) {
                // Ignore
            }
            operationExecutor.run(operation3)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            child1Id = operation.id
            assert operation.parentId == parentId
        }
        1 * operation2.run(_) >> { throw new RuntimeException() }
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == child1Id
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationDescriptor operation, OperationStartEvent start ->
            child2Id = operation.id
            assert operation.parentId == parentId
        }
        1 * operation3.run(_) >> {}
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == child2Id
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationDescriptor operation, OperationFinishEvent opResult ->
            assert operation.id == parentId
        }
    }

    def runnableBuildOperation(String name, Closure cl) {
        new RunnableBuildOperation() {
            void run(BuildOperationContext context) {
                cl.run()
            }

            BuildOperationDescriptor.Builder description() {
                return displayName(name)
            }
        }
    }

    static class TestRunnableBuildOperation implements RunnableBuildOperation {
        BuildOperationDescriptor.Builder description() { displayName("test") }

        String toString() { getClass().simpleName }

        void run(BuildOperationContext buildOperationContext) {}
    }
}
