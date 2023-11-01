/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.file.FileCollection
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.workspace.Workspace
import org.gradle.internal.file.TreeType

class ChangeOutputsStepTest extends StepSpec<InputChangesContext> {
    def outputChangeListener = Mock(OutputChangeListener)
    def delegateResult = Stub(Result)

    def step = new ChangeOutputsStep<>(outputChangeListener, delegate)

    def "notifies listener about specific outputs changing"() {
        def outputDir = file("output-dir")
        def localStateDir = file("local-state-dir")
        def destroyableDir = file("destroyable-dir")
        def changingOutputs = [
            outputDir.absolutePath,
            destroyableDir.absolutePath,
            localStateDir.absolutePath
        ]

        when:
        step.execute(work, context)

        then:
        _ * work.visitOutputs(_ as Workspace.WorkspaceLocation, _ as UnitOfWork.OutputVisitor) >> { Workspace.WorkspaceLocation workspace, UnitOfWork.OutputVisitor visitor ->
            visitor.visitOutputProperty("output", TreeType.DIRECTORY, UnitOfWork.OutputFileValueSupplier.fromStatic(outputDir, Mock(FileCollection)))
            visitor.visitDestroyable(destroyableDir)
            visitor.visitLocalState(localStateDir)
        }

        then:
        1 * outputChangeListener.invalidateCachesFor(changingOutputs)

        then:
        1 * delegate.execute(work, _ as ChangingOutputsContext) >> delegateResult
        then:
        1 * outputChangeListener.invalidateCachesFor(changingOutputs)
        then:
        0 * _
    }

}
