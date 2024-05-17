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

import spock.lang.Specification

import javax.annotation.Nullable

import static org.gradle.internal.operations.BuildOperationDescriptor.displayName
import static org.gradle.internal.operations.DefaultBuildOperationRunner.BuildOperationExecutionListener
import static org.gradle.internal.operations.DefaultBuildOperationRunner.ReadableBuildOperationContext
import static org.gradle.internal.operations.DefaultBuildOperationRunner.TimeSupplier

class DefaultBuildOperationRunnerTest extends Specification {

    def timeProvider = Mock(TimeSupplier)
    def listener = Mock(BuildOperationExecutionListener)
    def currentBuildOperationRef = CurrentBuildOperationRef.instance()
    def operationRunner = new DefaultBuildOperationRunner(currentBuildOperationRef, timeProvider, new DefaultBuildOperationIdFactory(), { listener })

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
        1 * timeProvider.currentTime >> 123L
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
        1 * timeProvider.currentTime >> 123L
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
        1 * timeProvider.currentTime >> 123L
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
        1 * timeProvider.currentTime >> 123L
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
}
