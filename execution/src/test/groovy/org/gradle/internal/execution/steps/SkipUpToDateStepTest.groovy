/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.caching.internal.origin.OriginMetadata
import org.gradle.internal.Try
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.ExecutionResult
import org.gradle.internal.execution.history.AfterExecutionState
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.history.changes.ExecutionStateChanges

class SkipUpToDateStepTest extends StepSpec<IncrementalChangesContext> {
    def step = new SkipUpToDateStep<>(delegate)
    def changes = Mock(ExecutionStateChanges)

    @Override
    protected IncrementalChangesContext createContext() {
        Stub(IncrementalChangesContext)
    }

    def "skips when outputs are up to date"() {
        when:
        def result = step.execute(work, context)

        then:
        result.executionResult.get().outcome == ExecutionOutcome.UP_TO_DATE
        !result.executionReasons.present

        _ * context.changes >> Optional.of(changes)
        _ * context.rebuildReasons >> ImmutableList.of()
        1 * changes.beforeExecutionState >> Mock(BeforeExecutionState)
        _ * context.previousExecutionState >> Optional.of(Mock(PreviousExecutionState) {
            1 * getOutputFilesProducedByWork() >> ImmutableSortedMap.of()
            1 * getOriginMetadata() >> Mock(OriginMetadata)
        })
        0 * _
    }

    def "executes when outputs are not up to date"() {
        def delegateResult = Mock(AfterExecutionResult)
        def delegateOutcome = Try.successful(Mock(ExecutionResult))
        def delegateAfterExecutionState = Mock(AfterExecutionState)

        when:
        def result = step.execute(work, context)

        then:
        result.executionReasons == ["change"]

        _ * context.changes >> Optional.of(changes)
        _ * context.rebuildReasons >> ImmutableList.of("change")
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        when:
        def outcome = result.executionResult

        then:
        outcome == delegateOutcome

        1 * delegateResult.executionResult >> delegateOutcome
        0 * _

        when:
        def afterExecutionState = result.afterExecutionState

        then:
        afterExecutionState.get() == delegateAfterExecutionState

        1 * delegateResult.afterExecutionState >> Optional.of(delegateAfterExecutionState)
        0 * _
    }

    def "executes when change tracking is disabled"() {
        when:
        def result = step.execute(work, context)

        then:
        _ * context.changes >> Optional.empty()
        1 * delegate.execute(work, context)
        0 * _
    }
}
