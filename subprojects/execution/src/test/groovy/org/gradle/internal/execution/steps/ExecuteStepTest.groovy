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

import com.google.common.collect.ImmutableMultimap
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.IncrementalChangesContext
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.execution.history.changes.InputChangesInternal
import spock.lang.Specification
import spock.lang.Unroll

class ExecuteStepTest extends Specification {
    def step = new ExecuteStep<IncrementalChangesContext>()
    def context = Mock(IncrementalChangesContext)
    def work = Mock(UnitOfWork)
    def changes = Mock(ExecutionStateChanges)
    def optionalChanges = Optional.of(changes)
    def inputChanges = Mock(InputChangesInternal)

    @Unroll
    def "result #workResult yields outcome #outcome (incremental false)"() {
        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == outcome

        1 * context.work >> work
        1 * work.requiresInputChanges >> false
        1 * work.execute(null) >> workResult
        0 * _

        where:
        workResult                        | outcome
        UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
    }

    @Unroll
    def "failure #failure.class.simpleName is not caught"() {
        when:
        step.execute(context)

        then:
        def ex = thrown Throwable
        ex == failure

        1 * context.work >> work
        1 * work.requiresInputChanges >> false
        1 * work.execute(null) >> { throw failure }
        0 * _

        where:
        failure << [new RuntimeException(), new Error()]
    }

    @Unroll
    def "incremental work with result #workResult yields outcome #outcome (executed incrementally: #incrementalExecution)"() {
        when:
        def result = step.execute(context)

        then:
        result.outcome.get() == outcome

        1 * context.work >> work
        1 * work.requiresInputChanges >> true
        1 * context.changes >> optionalChanges
        1 * work.visitInputFileProperties(_) >> { args ->
            ((UnitOfWork.InputFilePropertyVisitor) args[0]).visitInputFileProperty("fileInput", "some/path", true)
        }
        1 * changes.createInputChanges(ImmutableMultimap.of("some/path", "fileInput")) >> inputChanges
        1 * work.execute(inputChanges) >> workResult
        _ * work.getDisplayName()
        2 * inputChanges.incremental >> incrementalExecution
        0 * _

        where:
        incrementalExecution | workResult                        | outcome
        true                 | UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_INCREMENTALLY
        false                | UnitOfWork.WorkResult.DID_WORK    | ExecutionOutcome.EXECUTED_NON_INCREMENTALLY
        true                 | UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
        false                | UnitOfWork.WorkResult.DID_NO_WORK | ExecutionOutcome.UP_TO_DATE
    }
}
