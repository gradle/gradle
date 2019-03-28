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
import org.gradle.internal.execution.IncrementalChangesContext
import org.gradle.internal.execution.IncrementalContext
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector
import org.gradle.internal.execution.history.changes.ExecutionStateChanges

class ResolveChangesStepTest extends StepSpec {
    def changeDetector = Mock(ExecutionStateChangeDetector)
    def step = new ResolveChangesStep<Result>(changeDetector, delegate)
    def context = Mock(IncrementalContext)
    def beforeExecutionState = Mock(BeforeExecutionState)
    def delegateResult = Mock(Result)

    def "doesn't provide input file changes when rebuild is forced"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * context.work >> work
        1 * work.incrementality >> UnitOfWork.Incrementality.NOT_INCREMENTAL
        1 * delegate.execute(_) >> { IncrementalChangesContext delegateContext ->
            def changes = delegateContext.changes.get()
            assert changes.allChangeMessages == ImmutableList.of("Forced rebuild.")
            try {
                changes.createInputChanges()
                assert false
            } catch (UnsupportedOperationException e) {
                assert e.message == 'Cannot query input changes when input tracking is disabled.'
            }
            return delegateResult
        }
        1 * context.rebuildReason >> Optional.of("Forced rebuild.")
        1 * context.beforeExecutionState >> Optional.empty()
        0 * _
    }

    def "doesn't provide changes when change tracking is disabled"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * context.work >> work
        1 * delegate.execute(_) >> { IncrementalChangesContext delegateContext ->
            assert !delegateContext.changes.present
            return delegateResult
        }
        1 * context.rebuildReason >> Optional.empty()
        1 * context.beforeExecutionState >> Optional.empty()
        0 * _
    }

    def "doesn't provide input file changes when no history is available"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * context.work >> work
        1 * work.incrementality >> UnitOfWork.Incrementality.NOT_INCREMENTAL
        1 * delegate.execute(_) >> { IncrementalChangesContext delegateContext ->
            def changes = delegateContext.changes.get()
            assert !changes.createInputChanges().incremental
            assert changes.allChangeMessages == ImmutableList.of("No history is available.")
            return delegateResult
        }
        1 * context.rebuildReason >> Optional.empty()
        1 * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * beforeExecutionState.getInputFileProperties() >> ImmutableSortedMap.of()
        1 * context.afterPreviousExecutionState >> Optional.empty()
        0 * _
    }

    def "provides input file changes when history is available"() {
        def beforeExecutionState = Mock(BeforeExecutionState)
        def afterPreviousExecutionState = Mock(AfterPreviousExecutionState)
        def changes = Mock(ExecutionStateChanges)

        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * context.work >> work
        1 * delegate.execute(_) >> { IncrementalChangesContext delegateContext ->
            assert delegateContext.changes.get() == changes
            return delegateResult
        }
        1 * context.rebuildReason >> Optional.empty()
        1 * context.beforeExecutionState >> Optional.of(beforeExecutionState)
        1 * context.afterPreviousExecutionState >> Optional.of(afterPreviousExecutionState)
        1 * work.incrementality >> UnitOfWork.Incrementality.NOT_INCREMENTAL
        1 * work.allowOverlappingOutputs >> true
        1 * changeDetector.detectChanges(afterPreviousExecutionState, beforeExecutionState, work, false, _) >> changes
        0 * _
    }
}
