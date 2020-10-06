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

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.execution.WorkspaceContext
import org.gradle.internal.file.TreeType

class CreateOutputsStepTest extends StepSpec<WorkspaceContext> {
    def step = new CreateOutputsStep<>(delegate)

    @Override
    protected WorkspaceContext createContext() {
        Stub(WorkspaceContext)
    }

    def "outputs are created"() {
        def outputDir = file("outDir")
        def outputFile = file("parent/outFile")
        when:
        step.execute(context)

        then:
        _ * work.visitOutputProperties(_ as File, _ as UnitOfWork.OutputPropertyVisitor) >> { File workspace, UnitOfWork.OutputPropertyVisitor visitor ->
            visitor.visitOutputProperty("dir", TreeType.DIRECTORY, outputDir, TestFiles.fixed(outputDir))
            visitor.visitOutputProperty("file", TreeType.FILE, outputFile, TestFiles.fixed(outputFile))
        }

        then:
        file("outDir").isDirectory()

        def outFile = file("parent/outFile")
        outFile.parentFile.isDirectory()
        !outFile.exists()

        then:
        1 * delegate.execute(context)
        0 * _
    }

    def "result is preserved"() {
        def expected = Mock(Result)
        when:
        def result = step.execute(context)

        then:
        result == expected

        _ * work.visitOutputProperties(_ as File, _ as UnitOfWork.OutputPropertyVisitor)
        1 * delegate.execute(context) >> expected
        0 * _
    }
}
