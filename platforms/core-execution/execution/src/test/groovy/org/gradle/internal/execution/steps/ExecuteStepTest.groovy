/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.execution.ExecutionContext
import org.gradle.internal.execution.WorkOutput
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.history.changes.InputChangesInternal
import org.gradle.internal.operations.TestBuildOperationRunner

import static org.gradle.internal.execution.Execution.ExecutionOutcome.EXECUTED_INCREMENTALLY
import static org.gradle.internal.execution.Execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
import static org.gradle.internal.execution.Execution.ExecutionOutcome.UP_TO_DATE
import static org.gradle.internal.execution.WorkOutput.WorkResult.DID_NO_WORK
import static org.gradle.internal.execution.WorkOutput.WorkResult.DID_WORK

abstract class ExecuteStepTest<C extends WorkspaceContext> extends StepSpec<C> {
    def workspace = Mock(File)
    def buildOperationRunner = new TestBuildOperationRunner()
    def step = createStep()

    protected abstract ExecuteStep<C> createStep()

    def setup() {
        _ * context.getWorkspace() >> workspace
    }

    def "successful execution is handled"() {
        def executed = false

        when:
        def result = step.execute(work, context)

        then:
        result.execution.successful
        executed
        result.duration.toMillis() >= 100

        _ * context.inputChanges >> Optional.empty()
        _ * work.execute({ ExecutionContext executionRequest ->
            executionRequest.workspace == workspace
        }) >> {
            sleep 200
            executed = true
            return Stub(WorkOutput) {
                getDidWork() >> DID_WORK
            }
        }
        0 * _
    }

    def "failure #failure.class.simpleName is handled"() {
        when:
        def result = step.execute(work, context)

        then:
        !result.execution.successful
        result.execution.failure.get() == failure
        result.duration.toMillis() >= 100

        _ * context.inputChanges >> Optional.empty()
        _ * work.execute({ ExecutionContext executionRequest ->
            executionRequest.workspace == workspace
        }) >> {
            sleep 200
            throw failure
        }
        0 * _

        where:
        failure << [new RuntimeException(), new Error()]
    }
}

class ImmutableExecuteStepTest extends ExecuteStepTest<WorkspaceContext> {
    @Override
    protected ExecuteStep createStep() {
        return new ExecuteStep.Immutable(buildOperationRunner)
    }
}

class MutableExecuteStepTest extends ExecuteStepTest<InputChangesContext> {
    def previousOutputs = ImmutableSortedMap.of()
    def previousExecutionState = Stub(PreviousExecutionState) {
        getOutputFilesProducedByWork() >> previousOutputs
    }
    def inputChanges = Mock(InputChangesInternal)

    @Override
    protected ExecuteStep createStep() {
        return new ExecuteStep.Mutable(buildOperationRunner)
    }

    def setup() {
        _ * context.getPreviousExecutionState() >> Optional.of(previousExecutionState)
    }

    def "result #workResult yields outcome #expectedOutcome (incremental false)"() {
        def executed = false

        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == expectedOutcome
        // Check
        executed
        result.duration.toMillis() >= 100

        _ * context.inputChanges >> Optional.empty()
        _ * work.execute({ ExecutionContext executionRequest ->
            executionRequest.workspace == workspace && !executionRequest.inputChanges.present && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> {
            sleep 200
            executed = true
            Stub(WorkOutput) {
                getDidWork() >> workResult
            }
        }
        0 * _

        where:
        workResult  | expectedOutcome
        DID_WORK    | EXECUTED_NON_INCREMENTALLY
        DID_NO_WORK | UP_TO_DATE
    }

    def "incremental work with result #workResult yields outcome #expectedOutcome (executed incrementally: #incrementalExecution)"() {
        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == expectedOutcome

        _ * context.inputChanges >> Optional.of(inputChanges)
        _ * inputChanges.incremental >> incrementalExecution
        _ * work.execute({ ExecutionContext executionRequest ->
            executionRequest.workspace == workspace && executionRequest.inputChanges.get() == inputChanges && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> Stub(WorkOutput) {
            getDidWork() >> workResult
        }
        0 * _

        where:
        incrementalExecution | workResult  | expectedOutcome
        true                 | DID_WORK    | EXECUTED_INCREMENTALLY
        false                | DID_WORK    | EXECUTED_NON_INCREMENTALLY
        true                 | DID_NO_WORK | UP_TO_DATE
        false                | DID_NO_WORK | UP_TO_DATE
    }
}
