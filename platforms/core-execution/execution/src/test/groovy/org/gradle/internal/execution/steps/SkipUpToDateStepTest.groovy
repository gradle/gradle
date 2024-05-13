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
import org.gradle.internal.execution.history.AfterExecutionState
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.history.changes.ExecutionStateChanges

import java.time.Duration

import static org.gradle.internal.execution.ExecutionEngine.Execution
import static org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome.UP_TO_DATE

class SkipUpToDateStepTest extends StepSpec<IncrementalChangesContext> {
    def step = new SkipUpToDateStep<>(delegate)
    def changes = Mock(ExecutionStateChanges)
    def delegateResult = Stub(AfterExecutionResult)
    def delegateOriginMetadata = Stub(OriginMetadata) {
        getExecutionTime() >> Duration.ofSeconds(1)
    }

    def setup() {
        delegateResult.duration >> Duration.ofSeconds(1)
    }

    def "skips when outputs are up to date"() {
        when:
        def result = step.execute(work, context)

        then:
        result.execution.get().outcome == UP_TO_DATE
        !result.executionReasons.present

        _ * context.changes >> Optional.of(changes)
        _ * context.rebuildReasons >> ImmutableList.of()
        _ * context.previousExecutionState >> Optional.of(Stub(PreviousExecutionState) {
            getOutputFilesProducedByWork() >> ImmutableSortedMap.of()
            getOriginMetadata() >> delegateOriginMetadata
        })
        0 * _
    }

    def "executes when outputs are not up to date"() {
        def delegateOutcome = Try.successful(Mock(Execution))
        def delegateAfterExecutionState = Stub(AfterExecutionState)

        delegateResult.execution >> delegateOutcome
        delegateResult.afterExecutionOutputState >> Optional.of(delegateAfterExecutionState)

        when:
        def result = step.execute(work, context)

        then:
        result.executionReasons == ["change"]

        _ * context.changes >> Optional.of(changes)
        _ * context.rebuildReasons >> ImmutableList.of("change")
        1 * delegate.execute(work, context) >> delegateResult
        0 * _

        when:
        def outcome = result.execution

        then:
        outcome == delegateOutcome

        0 * _

        when:
        def afterExecutionState = result.afterExecutionOutputState

        then:
        afterExecutionState.get() == delegateAfterExecutionState

        0 * _
    }

    def "executes when change tracking is disabled"() {
        when:
        def result = step.execute(work, context)
        delegateResult.duration >> Duration.ofSeconds(1)

        then:
        _ * context.changes >> Optional.empty()
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }
}
