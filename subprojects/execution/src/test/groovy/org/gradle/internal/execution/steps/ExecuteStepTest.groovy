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
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.changes.InputChangesInternal
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Unroll

class ExecuteStepTest extends StepSpec<InputChangesContext> {
    def workspace = Mock(File)
    def previousOutputs = ImmutableSortedMap.of()
    def afterPreviousExecutionState = Stub(AfterPreviousExecutionState) {
        getOutputFilesProducedByWork() >> previousOutputs
    }

    def step = new ExecuteStep<>(new TestBuildOperationExecutor())
    def inputChanges = Mock(InputChangesInternal)

    @Override
    protected InputChangesContext createContext() {
        Stub(InputChangesContext)
    }

    def setup() {
        _ * context.getWorkspace() >> workspace
        _ * context.getAfterPreviousExecutionState() >> Optional.of(afterPreviousExecutionState)
    }

    @Unroll
    def "result #workResult yields outcome #expectedOutcome (incremental false)"() {
        when:
        def result = step.execute(work, context)

        then:
        result.executionResult.get().outcome == expectedOutcome

        _ * context.inputChanges >> Optional.empty()
        _ * work.execute({ UnitOfWork.ExecutionRequest executionRequest ->
            executionRequest.workspace == workspace && !executionRequest.inputChanges.present && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> Stub(UnitOfWork.WorkOutput) {
            getDidWork() >> workResult
        }
        0 * _

        where:
        workResult                        | expectedOutcome
        UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
    }

    @Unroll
    def "failure #failure.class.simpleName is handled"() {
        when:
        def result = step.execute(work, context)

        then:
        !result.executionResult.successful
        result.executionResult.failure.get() == failure

        _ * context.inputChanges >> Optional.empty()
        _ * work.execute({ UnitOfWork.ExecutionRequest executionRequest ->
            executionRequest.workspace == workspace && !executionRequest.inputChanges.present && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> { throw failure }
        0 * _

        where:
        failure << [new RuntimeException(), new Error()]
    }

    @Unroll
    def "incremental work with result #workResult yields outcome #expectedOutcome (executed incrementally: #incrementalExecution)"() {
        when:
        def result = step.execute(work, context)

        then:
        result.executionResult.get().outcome == expectedOutcome

        _ * context.inputChanges >> Optional.of(inputChanges)
        _ * inputChanges.incremental >> incrementalExecution
        _ * work.execute({ UnitOfWork.ExecutionRequest executionRequest ->
            executionRequest.workspace == workspace && executionRequest.inputChanges.get() == inputChanges && executionRequest.previouslyProducedOutputs.get() == previousOutputs
        }) >> Stub(UnitOfWork.WorkOutput) {
            getDidWork() >> workResult
        }
        0 * _

        where:
        incrementalExecution | workResult                        | expectedOutcome
        true                 | UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_INCREMENTALLY
        false                | UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        true                 | UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
        false                | UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
    }
}
