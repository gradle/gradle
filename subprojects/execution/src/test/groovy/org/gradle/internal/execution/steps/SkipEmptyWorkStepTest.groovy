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
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.snapshot.FileSystemSnapshot
import spock.lang.Unroll

class SkipEmptyWorkStepTest extends StepSpec<PreviousExecutionContext> {
    def step = new SkipEmptyWorkStep<>(delegate)
    def previousExecutionState = Mock(PreviousExecutionState)

    def delegateResult = Mock(CachingResult)
    def outputSnapshots = ImmutableSortedMap.<String, FileSystemSnapshot>of()
    def executionHistoryStore = Mock(ExecutionHistoryStore)

    @Override
    protected PreviousExecutionContext createContext() {
        Stub(PreviousExecutionContext)
    }

    def setup() {
        _ * context.history >> Optional.of(executionHistoryStore)
    }

    def "delegates when work is not skipped"() {
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult

        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.outputFilesProducedByWork >> outputSnapshots
        _ * work.skipIfInputsEmpty(outputSnapshots) >> Optional.empty()

        then:
        1 * delegate.execute(work, context) >> delegateResult
        0 * _
    }

    @Unroll
    def "captures no state when when empty work is skipped (outcome: #outcome)"() {
        when:
        def result = step.execute(work, context)

        then:
        result.executionResult.get().outcome == outcome
        !result.afterExecutionState.present

        _ * context.previousExecutionState >> Optional.of(previousExecutionState)
        1 * previousExecutionState.outputFilesProducedByWork >> outputSnapshots
        _ * work.skipIfInputsEmpty(outputSnapshots) >> Optional.of(outcome)
        0 * _

        where:
        outcome << [ExecutionOutcome.SHORT_CIRCUITED, ExecutionOutcome.EXECUTED_NON_INCREMENTALLY]
    }
}
