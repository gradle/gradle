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
import org.gradle.internal.execution.Executable
import org.gradle.internal.execution.ExecutionOutput
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.history.changes.InputChangesInternal
import org.gradle.internal.execution.workspace.Workspace
import org.gradle.internal.operations.TestBuildOperationExecutor

import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.EXECUTED_INCREMENTALLY
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE
import static org.gradle.internal.execution.WorkResult.DID_NO_WORK
import static org.gradle.internal.execution.WorkResult.DID_WORK

class ExecuteStepTest extends StepSpec<ChangingOutputsContext> {
    def workspace = Mock(Workspace.WorkspaceLocation)
    def previousOutputs = ImmutableSortedMap.of()
    def previousExecutionState = Stub(PreviousExecutionState) {
        getOutputFilesProducedByWork() >> previousOutputs
    }

    def executable = Mock(Executable)

    def step = new ExecuteStep<>(new TestBuildOperationExecutor())
    def inputChanges = Mock(InputChangesInternal)


    def setup() {
        _ * context.getMutableWorkspace() >> workspace
        _ * context.getPreviousExecutionState() >> Optional.of(previousExecutionState)
        _ * context.getExecutable() >> executable
    }

    def "result #workResult yields outcome #expectedOutcome (incremental false)"() {
        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == expectedOutcome
        // Check
        result.duration.toMillis() >= 100

        _ * context.inputChanges >> Optional.empty()
        1 * executable.execute({ Executable.ExecutionRequest executionRequest ->
            executionRequest.workspace == workspace && !executionRequest.inputChanges.present && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> {
            sleep 200
            Stub(ExecutionOutput) {
                getDidWork() >> workResult
            }
        }
        0 * _

        where:
        workResult  | expectedOutcome
        DID_WORK    | EXECUTED_NON_INCREMENTALLY
        DID_NO_WORK | UP_TO_DATE
    }

    def "failure #failure.class.simpleName is handled"() {
        when:
        def result = step.execute(work, context)

        then:
        !result.execution.successful
        result.execution.failure.get() == failure
        result.duration.toMillis() >= 100

        _ * context.inputChanges >> Optional.empty()
        1 * executable.execute({ Executable.ExecutionRequest executionRequest ->
            executionRequest.workspace == workspace && !executionRequest.inputChanges.present && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> {
            sleep 200
            throw failure
        }
        0 * _

        where:
        failure << [new RuntimeException(), new Error()]
    }

    def "incremental work with result #workResult yields outcome #expectedOutcome (executed incrementally: #incrementalExecution)"() {
        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == expectedOutcome

        _ * context.inputChanges >> Optional.of(inputChanges)
        _ * inputChanges.incremental >> incrementalExecution
        1 * executable.execute({ Executable.ExecutionRequest executionRequest ->
            executionRequest.workspace == workspace && executionRequest.inputChanges.get() == inputChanges && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> Stub(ExecutionOutput) {
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
