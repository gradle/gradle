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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.Sets
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.WorkResults
import org.gradle.cache.ObjectHolder
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class IncrementalNativeCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def delegateCompiler = Mock(Compiler)
    def outputs = Mock(TaskOutputsInternal)
    def compileStateCache = Mock(ObjectHolder)
    def incrementalCompilation = Mock(IncrementalCompilation)
    def deleter = TestFiles.deleter()
    def compiler = new IncrementalNativeCompiler(outputs, delegateCompiler, deleter, compileStateCache, incrementalCompilation)

    def "updates spec for incremental compilation"() {
        def spec = Mock(NativeCompileSpec)
        def newSource = temporaryFolder.file("new")
        def removedSource = temporaryFolder.file("removed")

        def compilation = Mock(IncrementalCompilation)

        when:
        compilation.getRecompile() >> [newSource]
        compilation.getRemoved() >> [removedSource]

        and:
        compiler.doIncrementalCompile(compilation, spec)

        then:
        1 * spec.setSourceFiles([newSource])
        1 * spec.setRemovedSourceFiles([removedSource])
        1 * delegateCompiler.execute(spec)
    }

    def "cleans outputs and delegates spec for clean compilation"() {
        def spec = Mock(NativeCompileSpec)
        def existingSource = temporaryFolder.file("existing")
        def newSource = temporaryFolder.file("new")
        def outputFile = temporaryFolder.createFile("output", "previous")

        def sources = [existingSource, newSource]

        when:
        outputFile.assertExists()

        and:
        spec.incrementalCompile >> false
        spec.getSourceFiles() >> sources
        spec.getPreCompiledHeader() >> null

        and:
        def result = compiler.doCleanIncrementalCompile(spec)

        then:
        1 * spec.getObjectFileDir() >> outputFile.parentFile
        1 * outputs.previousOutputFiles >> Sets.newHashSet(outputFile)
        1 * spec.setSourceFilesForPch(_)
        0 * spec._
        1 * delegateCompiler.execute(spec) >> WorkResults.didWork(false)

        and:
        result.didWork
        outputFile.assertDoesNotExist()
    }
}
