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

import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class IncrementalNativeCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def delegateCompiler = Mock(org.gradle.api.internal.tasks.compile.Compiler)
    def incrementalCompileProcessor = Mock(IncrementalCompileProcessor)
    def compiler = new IncrementalNativeCompiler(null, null, null, delegateCompiler)

    def "updates spec for incremental compilation"() {
        def spec = Mock(NativeCompileSpec)
        def existingSource = temporaryFolder.file("existing")
        def newSource = temporaryFolder.file("new")
        def removedSource = temporaryFolder.file("removed")

        def sources = [existingSource, newSource]
        def compilation = Mock(IncrementalCompilation)

        when:
        spec.getSourceFiles() >> sources
        incrementalCompileProcessor.processSourceFiles(_) >> compilation
        compilation.getRecompile() >> [newSource]
        compilation.getRemoved() >> [removedSource]

        and:
        compiler.doIncrementalCompile(incrementalCompileProcessor, spec)

        then:
        1 * spec.setSourceFiles([newSource])
        1 * spec.setRemovedSourceFiles([removedSource])
        1 * delegateCompiler.execute(spec)
    }
}
