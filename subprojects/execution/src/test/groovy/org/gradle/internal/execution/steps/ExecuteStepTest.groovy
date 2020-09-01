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

import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.InputChangesContext
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.changes.InputChangesInternal
import spock.lang.Unroll

class ExecuteStepTest extends StepSpec<InputChangesContext> {
    def step = new ExecuteStep<>()
    def inputChanges = Mock(InputChangesInternal)

    @Override
    protected InputChangesContext createContext() {
        Stub(InputChangesContext)
    }

    @Unroll
    def "result #workResult yields outcome #expectedOutcome (incremental false)"() {
        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == expectedOutcome

        _ * context.inputChanges >> Optional.empty()
        _ * work.execute(null, context) >> workResult
        0 * _

        where:
        workResult                        | expectedOutcome
        UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
    }

    @Unroll
    def "failure #failure.class.simpleName is handled"() {
        when:
        def result = step.execute(context)

        then:
        !result.outcome.successful
        result.outcome.failure.get() == failure

        _ * context.inputChanges >> Optional.empty()
        _ * work.execute(null, context) >> { throw failure }
        0 * _

        where:
        failure << [new RuntimeException(), new Error()]
    }

    @Unroll
    def "incremental work with result #workResult yields outcome #outcome (executed incrementally: #incrementalExecution)"() {
        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == expectedOutcome

        _ * context.inputChanges >> Optional.of(inputChanges)
        1 * inputChanges.incremental >> incrementalExecution
        _ * work.execute(inputChanges, context) >> workResult
        0 * _

        where:
        incrementalExecution | workResult                        | expectedOutcome
        true                 | UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_INCREMENTALLY
        false                | UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        true                 | UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
        false                | UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
    }
}
