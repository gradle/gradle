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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.problems.internal.Problem
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector
import org.gradle.internal.execution.history.changes.ExecutionStateChanges

class ResolveChangesStepTest extends StepSpec<ValidationFinishedContext> {
    def changeDetector = Mock(ExecutionStateChangeDetector)
    def step = new ResolveChangesStep<>(changeDetector, delegate)
    def beforeExecutionState = Stub(BeforeExecutionState) {
        inputFileProperties >> ImmutableSortedMap.of()
        inputProperties >> ImmutableSortedMap.of()
        outputFileLocationSnapshots >> ImmutableSortedMap.of()
    }
    def delegateResult = Mock(Result)

    def "doesn't provide input file changes when rebuild is forced"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        _ * work.executionBehavior >> UnitOfWork.ExecutionBehavior.NON_INCREMENTAL
        1 * delegate.execute(work, _ as IncrementalChangesContext) >> { UnitOfWork work, IncrementalChangesContext delegateContext ->
            def changes = delegateContext.changes.get()
            assert delegateContext.rebuildReasons == ImmutableList.of("Forced rebuild.")
            assert !changes.createInputChanges().incremental
            return delegateResult
        }
        _ * context.nonIncrementalReason >> Optional.of("Forced rebuild.")
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        0 * _
    }

    def "doesn't provide changes when change tracking is disabled"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        1 * delegate.execute(work, _ as IncrementalChangesContext) >> { UnitOfWork work, IncrementalChangesContext delegateContext ->
            return delegateResult
        }
        _ * context.nonIncrementalReason >> Optional.empty()
        _ * context.beforeExecutionState >> Optional.empty()
        0 * _
    }

    def "doesn't provide input file changes when no history is available"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        _ * work.executionBehavior >> UnitOfWork.ExecutionBehavior.NON_INCREMENTAL
        1 * delegate.execute(work, _ as IncrementalChangesContext) >> { UnitOfWork work, IncrementalChangesContext delegateContext ->
            def changes = delegateContext.changes.get()
            assert !changes.createInputChanges().incremental
            assert delegateContext.rebuildReasons == ImmutableList.of("No history is available.")
            return delegateResult
        }
        _ * context.nonIncrementalReason >> Optional.empty()
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * context.previousExecutionState >> Optional.empty()
        0 * _
    }

    def "doesn't provide input file changes when work fails validation"() {
        def previousExecutionState = Mock(PreviousExecutionState)
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        _ * work.executionBehavior >> UnitOfWork.ExecutionBehavior.NON_INCREMENTAL
        1 * delegate.execute(work, _ as IncrementalChangesContext) >> { UnitOfWork work, IncrementalChangesContext delegateContext ->
            def changes = delegateContext.changes.get()
            assert !changes.createInputChanges().incremental
            assert delegateContext.rebuildReasons == ImmutableList.of("Incremental execution has been disabled to ensure correctness. Please consult deprecation warnings for more details.")
            return delegateResult
        }
        _ * context.nonIncrementalReason >> Optional.empty()
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        _ * context.validationProblems >> ImmutableList.of(Mock(Problem))
        _ * context.previousExecutionState >> Optional.empty()
        0 * _
    }

    def "provides input file changes when history is available"() {
        def previousExecutionState = Mock(PreviousExecutionState)
        def changes = Mock(ExecutionStateChanges)

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        1 * delegate.execute(work, _ as IncrementalChangesContext) >> { UnitOfWork work, IncrementalChangesContext delegateContext ->
            assert delegateContext.changes.get() == changes
            return delegateResult
        }
        _ * changes.changeDescriptions >> ImmutableList.of("changed")
        _ * context.nonIncrementalReason >> Optional.empty()
        _ * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        _ * context.validationProblems >> ImmutableList.of()
        _ * work.executionBehavior >> UnitOfWork.ExecutionBehavior.NON_INCREMENTAL
        1 * changeDetector.detectChanges(work, previousExecutionState, beforeExecutionState, _) >> changes
        0 * _
    }

}
