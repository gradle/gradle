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

import org.gradle.internal.execution.MutableUnitOfWork
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.workspace.MutableWorkspaceProvider

class AssignMutableWorkspaceStepTest extends StepSpec<IdentityContext> {
    def delegateResult = Mock(Result)
    def step = new AssignMutableWorkspaceStep<>(delegate)
    def work = Stub(MutableUnitOfWork)

    def "delegates with assigned mutable workspace"() {
        def workspace = file("workspace")
        def workspaceProvider = Mock(MutableWorkspaceProvider)
        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        _ * work.workspaceProvider >> workspaceProvider
        1 * workspaceProvider.withWorkspace(":test", _) >> { String identity, MutableWorkspaceProvider.WorkspaceAction action ->
            def actionResult = action.executeInWorkspace(workspace, null)
            return actionResult
        }
        1 * delegate.execute(work, _ as WorkspaceContext) >> { UnitOfWork work, WorkspaceContext context ->
            assert context.workspace == workspace
            return delegateResult
        }
    }
}
