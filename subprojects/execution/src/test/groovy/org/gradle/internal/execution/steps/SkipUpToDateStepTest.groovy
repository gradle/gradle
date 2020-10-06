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
import org.gradle.internal.Try
import org.gradle.internal.execution.CurrentSnapshotResult
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.IncrementalChangesContext
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.history.AfterPreviousExecutionState
import org.gradle.internal.execution.history.changes.ExecutionStateChanges
import org.gradle.internal.fingerprint.impl.EmptyCurrentFileCollectionFingerprint

class SkipUpToDateStepTest extends StepSpec<IncrementalChangesContext> {
    def step = new SkipUpToDateStep<>(delegate)
    def changes = Mock(ExecutionStateChanges)

    @Override
    protected IncrementalChangesContext createContext() {
        Stub(IncrementalChangesContext)
    }

    def "skips when outputs are up to date"() {
        when:
        def result = step.execute(context)

        then:
        result.executionResult.get().outcome == ExecutionOutcome.UP_TO_DATE
        !result.executionReasons.present

        _ * context.changes >> Optional.of(changes)
        1 * changes.allChangeMessages >> ImmutableList.of()
        _ * context.afterPreviousExecutionState >> Optional.of(Mock(AfterPreviousExecutionState))
        0 * _
    }

    def "executes when outputs are not up to date"() {
        def delegateResult = Mock(CurrentSnapshotResult)
        def delegateOutcome = Try.successful(Mock(Result.ExecutionResult))
        def delegateFinalOutputs = ImmutableSortedMap.copyOf([test: EmptyCurrentFileCollectionFingerprint.EMPTY])

        when:
        def result = step.execute(context)

        then:
        result.executionReasons == ["change"]
        !result.reusedOutputOriginMetadata.present

        _ * context.changes >> Optional.of(changes)
        1 * changes.allChangeMessages >> ImmutableList.of("change")
        1 * delegate.execute(context) >> delegateResult
        0 * _

        when:
        def outcome = result.executionResult

        then:
        outcome == delegateOutcome

        1 * delegateResult.executionResult >> delegateOutcome
        0 * _

        when:
        def finalOutputs = result.finalOutputs

        then:
        finalOutputs == delegateFinalOutputs

        1 * delegateResult.finalOutputs >> delegateFinalOutputs
        0 * _
    }

    def "executes when change tracking is disabled"() {
        when:
        def result = step.execute(context)

        then:
        result.executionReasons == ["Change tracking is disabled."]

        _ * context.changes >> Optional.empty()
        1 * delegate.execute(context)
        0 * _
    }
}
