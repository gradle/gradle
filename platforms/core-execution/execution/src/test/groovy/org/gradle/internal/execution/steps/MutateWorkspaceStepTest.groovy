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

import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.workspace.Workspace

class MutateWorkspaceStepTest extends StepSpec<WorkspaceContext> {
    def delegateResult = Mock(Result)
    def step = new MutateWorkspaceStep(delegate)

    def "delegates with mutable workspace"() {
        def workspaceDir = file("workspace")
        def workspace = Stub(Workspace) {
            mutate(_ as Workspace.WorkspaceAction) >> { Workspace.WorkspaceAction<?> action ->
                action.executeInWorkspace(workspaceDir)
            }
        }

        when:
        def result = step.execute(work, context)

        then:
        result == delegateResult
        _ * context.workspace >> workspace
        1 * delegate.execute(work, _ as MutableWorkspaceContext) >> { UnitOfWork work, MutableWorkspaceContext context ->
            assert context.mutableWorkspaceLocation == workspaceDir
            return delegateResult
        }
    }
}
