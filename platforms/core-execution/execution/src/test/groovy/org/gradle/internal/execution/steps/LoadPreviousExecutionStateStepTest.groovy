/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.history.ExecutionHistoryStore
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.execution.workspace.WorkspaceProvider

class LoadPreviousExecutionStateStepTest extends StepSpec<IdentityContext> {
    def executionHistoryStore = Mock(ExecutionHistoryStore)

    def step = new LoadPreviousExecutionStateStep(delegate)
    def uniqueId = "test"
    def identity = Stub(UnitOfWork.Identity) {
        getUniqueId() >> uniqueId
    }
    def previousExecutionState = Stub(PreviousExecutionState)
    def delegateResult = Mock(AfterExecutionResult)

    def setup() {
        _ * context.identity >> identity
        _ * work.workspaceProvider >> Stub(WorkspaceProvider) {
            history >> Optional.of(executionHistoryStore)
        }
    }

    def "loads execution history and removes untracked outputs when needed"() {
        when:
        def result = step.execute(work, context)

        then:
        1 * executionHistoryStore.load(uniqueId) >> Optional.of(previousExecutionState)

        then:
        result == delegateResult
        1 * delegate.execute(work, _) >> { UnitOfWork work, PreviousExecutionContext previousExecutionContext ->
            assert previousExecutionContext.previousExecutionState.get() == previousExecutionState
            return delegateResult
        }

        then:
        1 * delegateResult.afterExecutionState >> Optional.empty()
        1 * executionHistoryStore.remove(identity.uniqueId)
    }
}
