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
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.file.TreeType

class PreCreateOutputParentsStepTest extends StepSpec<ChangingOutputsContext> {
    def step = new PreCreateOutputParentsStep<>(delegate)

    def "outputs are created"() {
        given:
        def outputDir = file("outDir")
        def outputFile = file("parent/outFile")
        def localStateFile = file("local-state/stateFile")
        def destroyableFile = file("destroyable/file.txt")

        when:
        step.execute(work, context)

        then:
        _ * work.visitOutputs(_ as File, _ as UnitOfWork.OutputVisitor) >> { File workspace, UnitOfWork.OutputVisitor visitor ->
            visitor.visitOutputProperty("dir", TreeType.DIRECTORY, UnitOfWork.OutputFileValueSupplier.fromStatic(outputDir, TestFiles.fixed(outputDir)))
            visitor.visitOutputProperty("file", TreeType.FILE, UnitOfWork.OutputFileValueSupplier.fromStatic(outputFile, TestFiles.fixed(outputFile)))
            visitor.visitLocalState(localStateFile)
            visitor.visitDestroyable(destroyableFile)
        }

        then:
        outputDir.assertIsEmptyDir()
        outputFile.parentFile.assertIsEmptyDir()
        localStateFile.parentFile.assertIsEmptyDir()
        !destroyableFile.parentFile.exists()

        then:
        1 * delegate.execute(work, context)
        0 * _
    }

    def "result is preserved"() {
        def expected = Mock(Result)
        when:
        def result = step.execute(work, context)

        then:
        result == expected

        _ * work.visitOutputs(_ as File, _ as UnitOfWork.OutputVisitor)
        1 * delegate.execute(work, context) >> expected
        0 * _
    }
}
