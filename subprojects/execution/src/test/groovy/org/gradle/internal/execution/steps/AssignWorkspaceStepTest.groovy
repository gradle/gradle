/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.execution.IdentityContext
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkspaceContext

class AssignWorkspaceStepTest extends StepSpec<IdentityContext> {
    def delegateResult = Mock(Result)
    def step = new AssignWorkspaceStep<>(delegate)

    @Override
    protected IdentityContext createContext() {
        Stub(IdentityContext)
    }

    def "delegates with assigned workspace"() {
        def workspace = file("workspace")
        when:
        def result = step.execute(context)

        then:
        result == delegateResult
        _ * work.withWorkspace(":test", _) >> { String identity, UnitOfWork.WorkspaceAction action ->
            def actionResult = action.executeInWorkspace(workspace)
            return actionResult
        }
        1 * delegate.execute(_) >> { WorkspaceContext context ->
            assert context.workspace == workspace
            return delegateResult
        }
    }
}
