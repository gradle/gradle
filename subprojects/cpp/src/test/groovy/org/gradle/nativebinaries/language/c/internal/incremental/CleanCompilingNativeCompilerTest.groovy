/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.language.c.internal.incremental

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CleanCompilingNativeCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def delegateCompiler = Mock(org.gradle.api.internal.tasks.compile.Compiler)
    def incrementalCompileProcessor = Mock(IncrementalCompileProcessor)
    def task = Mock(TaskInternal)
    def outputs = Mock(TaskOutputsInternal)
    def compiler = new CleanCompilingNativeCompiler(task, null, null, delegateCompiler)

    def "cleans outputs and delegates spec for compilation"() {
        def spec = Mock(NativeCompileSpec)
        def existingSource = temporaryFolder.file("existing")
        def newSource = temporaryFolder.file("new")
        def outputFile = temporaryFolder.createFile("output", "previous")

        def sources = [existingSource, newSource]
        def compilation = Mock(IncrementalCompilation)

        when:
        outputFile.assertExists()

        and:
        spec.getSourceFiles() >> sources
        incrementalCompileProcessor.processSourceFiles(_) >> compilation
        0 * compilation._

        and:
        compiler.doIncrementalCompile(incrementalCompileProcessor, spec)

        then:
        1 * spec.getObjectFileDir() >> outputFile.parentFile
        1 * task.getOutputs() >> outputs
        1 * outputs.previousFiles >> new SimpleFileCollection(outputFile)
        0 * spec._
        1 * delegateCompiler.execute(spec)

        and:
        outputFile.assertDoesNotExist()
    }
}
