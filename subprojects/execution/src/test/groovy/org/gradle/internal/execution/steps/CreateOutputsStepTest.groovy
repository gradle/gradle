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

import org.gradle.api.internal.file.collections.ImmutableFileCollection
import org.gradle.internal.execution.Context
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.file.TreeType

class CreateOutputsStepTest extends StepSpec {
    def context = Stub(Context) {
        getWork() >> work
    }
    def step = new CreateOutputsStep<Context, Result>(delegate)

    def "outputs are created"() {
        when:
        step.execute(context)

        then:
        1 * work.visitOutputProperties(_ as UnitOfWork.OutputPropertyVisitor) >> { UnitOfWork.OutputPropertyVisitor visitor ->
            visitor.visitOutputProperty("dir", TreeType.DIRECTORY, ImmutableFileCollection.of(file("outDir")))
            visitor.visitOutputProperty("dirs", TreeType.DIRECTORY, ImmutableFileCollection.of(file("outDir1"), file("outDir2")))
            visitor.visitOutputProperty("file", TreeType.FILE, ImmutableFileCollection.of(file("parent/outFile")))
            visitor.visitOutputProperty("files", TreeType.FILE, ImmutableFileCollection.of(file("parent1/outFile"), file("parent2/outputFile1"), file("parent2/outputFile2")))
        }

        then:
        def allDirs = ["outDir", "outDir1", "outDir2"].collect { file(it) }
        def allFiles = ["parent/outFile", "parent1/outFile1", "parent2/outFile1", "parent2/outFile2"].collect { file(it) }
        allDirs.each {
            assert it.isDirectory()
        }
        allFiles.each {
            assert it.parentFile.isDirectory()
            assert !it.exists()
        }

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
        1 * work.visitOutputProperties(_ as UnitOfWork.OutputPropertyVisitor)
        1 * delegate.execute(context) >> expected
        0 * _
    }
}
