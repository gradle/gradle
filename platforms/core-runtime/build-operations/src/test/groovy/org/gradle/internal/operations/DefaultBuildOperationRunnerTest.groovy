/*
 * Copyright 2020 the original author or authors.
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


import org.gradle.internal.time.FixedClock
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import javax.annotation.Nullable

import static org.gradle.internal.operations.BuildOperationDescriptor.displayName
import static org.gradle.internal.operations.DefaultBuildOperationRunner.BuildOperationExecutionListener
import static org.gradle.internal.operations.DefaultBuildOperationRunner.ReadableBuildOperationContext

class DefaultBuildOperationRunnerTest extends ConcurrentSpec {

    def clock = FixedClock.createAt(123L)
    def listener = Mock(BuildOperationExecutionListener)
    def currentBuildOperationRef = CurrentBuildOperationRef.instance()
    def operationRunner = new DefaultBuildOperationRunner(
        currentBuildOperationRef,
        clock, new DefaultBuildOperationIdFactory(), { listener })

    def setup() {
        currentBuildOperationRef.clear()
    }

    def "fires events when wrap-around operation starts and finishes successfully with '#defaultParent' parent"() {
        setup:
        def buildOperation = Mock(BuildOperation)
        def details = Mock(Object)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>").details(details)
        def worker = Mock(BuildOperationWorker)
        BuildOperationDescriptor descriptorUnderTest
        BuildOperationState operationStateUnderTest

        when:
        operationRunner.execute(buildOperation, worker, defaultParent)

        then:
        1 * buildOperation.description() >> operationDetailsBuilder
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            descriptorUnderTest = descriptor
            operationStateUnderTest = operationState
            assert descriptor.id != null
            assert descriptor.parentId == defaultParent?.id
            assert descriptor.name == "<op>"
            assert descriptor.displayName == "<some-operation>"
            assert descriptor.details == details
            assert operationState.startTime == 123L
        }

        then:
        1 * worker.execute(buildOperation, _) >> { BuildOperation operation, BuildOperationContext context ->
            context.result = "result"
            context.status = "status"
        }

        then:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
            assert parent == defaultParent
            assert context.result == "result"
            assert context.status == "status"
            assert context.failure == null
        }

        then:
        1 * listener.close(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
        }

        where:
        defaultParent << [null, operation("parent")]
    }

    def "fires events when non-wrap-around start operation starts and finishes successfully"() {
        setup:
        def details = Mock(Object)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>").details(details)
        BuildOperationDescriptor descriptorUnderTest
        BuildOperationState operationStateUnderTest

        when:
        def contextUnderTest = operationRunner.start(operationDetailsBuilder)

        then:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            descriptorUnderTest = descriptor
            operationStateUnderTest = operationState
            assert descriptor.id != null
            assert descriptor.parentId == null
            assert descriptor.name == "<op>"
            assert descriptor.displayName == "<some-operation>"
            assert descriptor.details == details
            assert operationState.startTime == 123L
        }

        when:
        contextUnderTest.status = "status"
        contextUnderTest.result = "result"

        then:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
            assert parent == null
            assert context.result == "result"
            assert context.status == "status"
            assert context.failure == null
        }

        then:
        1 * listener.close(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
        }
    }

    def "fires events when wrap-around operation starts and fails via #description"() {
        setup:
        def buildOperation = Mock(BuildOperation)
        def details = Mock(Object)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>").details(details)
        def worker = Mock(BuildOperationWorker)
        BuildOperationDescriptor descriptorUnderTest
        BuildOperationState operationStateUnderTest

        when:
        try {
            operationRunner.execute(buildOperation, worker, null)
            assert expectedException == null
        } catch (Exception ex) {
            assert ex == expectedException
        }

        then:
        1 * buildOperation.description() >> operationDetailsBuilder
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            descriptorUnderTest = descriptor
            operationStateUnderTest = operationState
        }

        then:
        1 * worker.execute(buildOperation, _) >> { BuildOperation operation, BuildOperationContext context ->
            context.status = "status"
            fail.call(context, expectedException)
        }

        then:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
            assert context.result == null
            assert context.status == "status"
            assert context.failure.message == "Error"
        }

        then:
        1 * listener.close(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
        }

        where:
        description        | expectedException             | fail
        "thrown exception" | new RuntimeException("Error") | { BuildOperationContext context, Exception e -> throw e }
        "context"          | null                          | { BuildOperationContext context, Exception e -> context.failed(new RuntimeException("Error")) }
    }

    def "fires events when non-wrap-around start operation starts and fails"() {
        setup:
        def details = Mock(Object)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>").details(details)
        BuildOperationDescriptor descriptorUnderTest
        BuildOperationState operationStateUnderTest
        def failure = new RuntimeException("Error")

        when:
        def contextUnderTest = operationRunner.start(operationDetailsBuilder)

        then:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            descriptorUnderTest = descriptor
            operationStateUnderTest = operationState
        }

        when:
        contextUnderTest.status = "status"
        contextUnderTest.failed(failure)

        then:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
            assert context.result == null
            assert context.status == "status"
            assert context.failure == failure
        }

        then:
        1 * listener.close(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState ->
            assert descriptor.is(descriptorUnderTest)
            assert operationState.is(operationStateUnderTest)
        }
    }


    def "fails when non-wrap-around operation finishes after parent"() {
        BuildOperationContext operationContext = null
        operationRunner.run(runnableBuildOperation("parent") {
            operationContext = operationRunner.start(displayName("child"))
        })

        when:
        operationContext.result = "result"

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Parent operation (parent) completed before this operation (child).'
    }

    def "can query operation id from inside operation"() {
        expect:
        operationRunner.run(runnableBuildOperation("parent") {
            assert currentOperation.id != null
            assert currentOperation.parentId == null
            assert currentOperation.description.displayName == "parent"
            def parentId = currentOperation.id

            operationRunner.run(runnableBuildOperation("child") {
                assert currentOperation.id != null
                assert currentOperation.parentId == parentId
                assert currentOperation.description.displayName == "child"
            })
        })
    }

    def "action can mark operation as failed without throwing an exception"() {
        setup:
        def buildOperation = Spy(TestRunnableBuildOperation)
        def failure = new RuntimeException()

        when:
        operationRunner.run(buildOperation)

        then:
        1 * buildOperation.run(_) >> { BuildOperationContext context -> context.failed(failure) }

        then:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert context.failure == failure
        }
    }

    def "action can provide operation result"() {
        setup:
        def buildOperation = Spy(TestRunnableBuildOperation)
        def result = "SomeResult"

        when:
        operationRunner.run(buildOperation)

        then:
        1 * buildOperation.run(_) >> { BuildOperationContext context -> context.result = result }

        then:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert context.result == result
        }
    }

    def "action can provide progress updates as status string or items completed"() {
        setup:
        def buildOperation = Mock(RunnableBuildOperation)
        def operationDetailsBuilder = displayName("<some-operation>").name("<op>").progressDisplayName("<some-op>")

        when:
        operationRunner.run(buildOperation)

        then:
        1 * buildOperation.description() >> operationDetailsBuilder

        then:
        1 * buildOperation.run(_) >> { BuildOperationContext context ->
            context.progress("progress 1")
            context.progress("progress 2")
            context.progress(2, 4, "gold pieces", "progress 3")
        }

        then:
        1 * listener.progress(_, _) >> { BuildOperationDescriptor descriptor, String status ->
            assert status == "progress 1"
        }
        1 * listener.progress(_, _) >> { BuildOperationDescriptor descriptor, String status ->
            assert status == "progress 2"
        }
        1 * listener.progress(_, _, _, _, _) >> { BuildOperationDescriptor descriptor, long progress, long total, String units, String status ->
            assert progress == 2
            assert total == 4
            assert units == "gold pieces"
        }
    }

    def "multiple threads can run independent operations concurrently"() {
        def id1
        def id2

        when:
        async {
            start {
                operationRunner.run(runnableBuildOperation("<thread-1>") {
                    instant.action1Started
                    thread.blockUntil.action2Started
                })
            }
            thread.blockUntil.action1Started
            operationRunner.run(runnableBuildOperation("<thread-2>") {
                instant.action2Started
                thread.blockUntil.action1Finished
            })
        }

        then:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            id1 = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.toString() == "<thread-1>"
        }
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            id2 = operation.id
            assert operation.id != null
            assert operation.parentId == null
            assert operation.toString() == "<thread-2>"
        }
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert operation.id == id1
            assert context.failure == null
            instant.action1Finished
        }
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState parent, ReadableBuildOperationContext context ->
            assert operation.id == id2
            assert context.failure == null
        }
    }

    def "multiple threads can run child operations concurrently"() {
        setup:
        BuildOperationRef parent
        def id1
        def id2

        when:
        operationRunner.run(runnableBuildOperation("<main>") {
            parent = operationRunner.currentOperation
            async {
                start {
                    operationRunner.run(new RunnableBuildOperation() {
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
                    operationRunner.run(new RunnableBuildOperation() {
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
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            assert operation.id != null
            assert operation.parentId == null
            assert operation.toString() == "<main>"
        }
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            id1 = operation.id
            assert operation.id != null
            assert operation.parentId == parent.id
            assert operation.toString() == "<thread-1>"
        }
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            id2 = operation.id
            assert operation.id != null
            assert operation.parentId == parent.id
            assert operation.toString() == "<thread-2>"
        }
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == id1
            assert context.failure == null
            instant.action1Finished
        }
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == id2
            assert context.failure == null
        }
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == parent.id
            assert context.failure == null
        }
    }

    def "cannot start child operation when parent has completed"() {
        BuildOperationRef parent = null

        given:
        operationRunner.run(runnableBuildOperation("parent") {
            parent = operationRunner.currentOperation
        })

        when:
        operationRunner.run(new RunnableBuildOperation() {
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
            operationRunner.run(runnableBuildOperation("parent") {
                def operation = operationRunner.currentOperation
                start {
                    operationRunner.run(new RunnableBuildOperation() {
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

    def "can query operation id from inside operation"() {
        given:
        def parentOperation = Spy(TestRunnableBuildOperation)
        def childOperation = Spy(TestRunnableBuildOperation)
        def id

        when:
        operationRunner.run(parentOperation)

        then:
        1 * parentOperation.run(_) >> {
            assert operationRunner.currentOperation.id != null
            assert operationRunner.currentOperation.parentId == null
            id = operationRunner.currentOperation.id
            operationRunner.run(childOperation)
        }
        1 * childOperation.run(_) >> {
            assert operationRunner.currentOperation.id != null
            assert operationRunner.currentOperation.id != id
            assert operationRunner.currentOperation.parentId == id
        }
    }

    def "cannot query operation id when no operation running on current managed thread"() {
        when:
        async {
            start {
                operationRunner.run(runnableBuildOperation("operation") {
                    instant.operationRunning
                    thread.blockUntil.queried
                })
            }
            thread.blockUntil.operationRunning
            try {
                operationRunner.currentOperation.id
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
        async {
            operationRunner.currentOperation.getId()
        }

        then:
        def ex = thrown(IllegalStateException)
        ex.message == 'No operation is currently running.'
    }

    def "can nest operations on unmanaged threads"() {
        when:
        async {
            operationRunner.run(new RunnableBuildOperation() {
                void run(BuildOperationContext outerContext) {
                    assert operationRunner.currentOperation.id != null
                    assert operationRunner.currentOperation.parentId == null

                    operationRunner.run(new RunnableBuildOperation() {
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
        operationRunner.run(operation1)

        then:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            assert operation.id != null
            parentId = operation.id
            assert operation.parentId == null
        }
        1 * operation1.run(_) >> {
            operationRunner.run(operation2)
        }

        and:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            child1Id = operation.id
            assert operation.parentId == parentId
        }
        1 * operation2.run(_) >> {
            operationRunner.run(operation3)
        }

        and:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            child2Id = operation.id
            assert operation.parentId == child1Id
        }
        1 * operation3.run(_) >> {}

        and:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == child2Id
        }

        and:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == child1Id
        }

        and:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
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
                operationRunner.run(runnableBuildOperation("<parent-1>") {
                    operationRunner.run(runnableBuildOperation("<child-1>") {
                        instant.child1Started
                        thread.blockUntil.child2Started
                    })
                })
            }
            start {
                operationRunner.run(runnableBuildOperation("<parent-2>") {
                    operationRunner.run(runnableBuildOperation("<child-2>") {
                        instant.child2Started
                        thread.blockUntil.child1Started
                    })
                })
            }
        }

        then:
        1 * listener.start({ it.displayName == "<parent-1>" }, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            assert operation.id != null
            parent1Id = operation.id
            assert operation.parentId == null
        }
        1 * listener.start({ it.displayName == "<parent-2>" }, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            assert operation.id != null
            parent2Id = operation.id
            assert operation.parentId == null
        }
        1 * listener.start({ it.displayName == "<child-1>" }, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            assert operation.id != null
            child1Id = operation.id
            assert operation.parentId == parent1Id
        }
        1 * listener.start({ it.displayName == "<child-2>" }, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            assert operation.id != null
            child2Id = operation.id
            assert operation.parentId == parent2Id
        }

        and:
        1 * listener.stop({ it.id == child1Id }, _, _, _)
        1 * listener.stop({ it.id == child2Id }, _, _, _)
        1 * listener.stop({ it.id == parent1Id }, _, _, _)
        1 * listener.stop({ it.id == parent2Id }, _, _, _)
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
        operationRunner.run(operation1)

        then:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            parentId = operation.id
            assert operation.parentId == null
        }
        1 * operation1.run(_) >> {
            try {
                operationRunner.run(operation2)
            } catch (RuntimeException ignore) {
            }
            operationRunner.run(operation3)
        }

        and:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            child1Id = operation.id
            assert operation.parentId == parentId
        }
        1 * operation2.run(_) >> { throw new RuntimeException() }
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == child1Id
        }

        and:
        1 * listener.start(_, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation ->
            child2Id = operation.id
            assert operation.parentId == parentId
        }
        1 * operation3.run(_) >> {}
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == child2Id
        }

        and:
        1 * listener.stop(_, _, _, _) >> { BuildOperationDescriptor descriptor, BuildOperationState operation, @Nullable BuildOperationState p, ReadableBuildOperationContext context ->
            assert operation.id == parentId
        }
    }

    private BuildOperationState getCurrentOperation() {
        currentBuildOperationRef.get() as BuildOperationState
    }

    private static BuildOperationState operation(String description) {
        def operation = new BuildOperationState(displayName(description).build(), 100L)
        operation.running = true
        return operation
    }

    private static runnableBuildOperation(String name, Runnable action = {}) {
        new RunnableBuildOperation() {
            void run(BuildOperationContext context) {
                action.run()
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
