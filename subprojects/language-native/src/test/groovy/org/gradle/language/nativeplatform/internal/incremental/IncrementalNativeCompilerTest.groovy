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

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.changes.DiscoveredInputRecorder
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.SimpleWorkResult
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.nativeplatform.toolchain.Clang
import org.gradle.nativeplatform.toolchain.Gcc
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

@UsesNativeServices
class IncrementalNativeCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def delegateCompiler = Mock(Compiler)
    def toolChain = Mock(NativeToolChain)
    def task = Mock(TaskInternal)
    def directoryTreeFactory = TestFiles.directoryFileTreeFactory()
    def compiler = new IncrementalNativeCompiler(task, null, null, delegateCompiler, toolChain, directoryTreeFactory)

    def outputs = Mock(TaskOutputsInternal)

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
        task.outputs >> outputs
        spec.getSourceFiles() >> sources

        and:
        def result = compiler.doCleanIncrementalCompile(spec)

        then:
        1 * spec.getObjectFileDir() >> outputFile.parentFile
        1 * outputs.previousOutputFiles >> new SimpleFileCollection(outputFile)
        0 * spec._
        1 * delegateCompiler.execute(spec) >> new SimpleWorkResult(false)

        and:
        result.didWork
        outputFile.assertDoesNotExist()
    }

    @Unroll
    def "imports are includes for toolchain #tcName"() {
       when:
       def compiler = new IncrementalNativeCompiler(task, null, null, delegateCompiler, toolChain, directoryTreeFactory)
       then:
       compiler.importsAreIncludes
       where:
       tcName   | toolChain
       "clang"  | Mock(Clang)
       "gcc"    | Mock(Gcc)

    }

    def "adds include files as discovered inputs"() {
        given:
        def spec = Mock(NativeCompileSpec)
        def compilation = Mock(IncrementalCompilation)
        def taskInputs = Mock(DiscoveredInputRecorder)
        def includedFile = temporaryFolder.file("include")
        compilation.discoveredInputs >> [includedFile ]

        when:
        compiler.handleDiscoveredInputs(spec, compilation, taskInputs)

        then:
        1 * spec.getSourceFiles() >> []
        1 * taskInputs.newInput(includedFile)
        0 * spec._
    }

    def "falls back to old behavior of walking include path when macros are used"() {
        given:
        def spec = Mock(NativeCompileSpec)

        def taskInputs = Mock(DiscoveredInputRecorder)

        def includeDir = temporaryFolder.createDir("includes")
        def includedFile = includeDir.createFile("include")
        def notIncludedFile = includeDir.createFile("notIncluded")
        def sourceFile = temporaryFolder.file("source")
        def includeRoots = [ includeDir ]

        def compilation = Mock(IncrementalCompilation)
        def sourceState = Mock(CompilationFileState)
        def sourceFiles = ImmutableSet.copyOf([sourceFile])
        def map = ImmutableMap.copyOf(Collections.singletonMap(sourceFile, sourceState))
        def finalState = new CompilationState(sourceFiles, map)

        compilation.discoveredInputs >> [includedFile]
        compilation.getFinalState() >> finalState
        sourceState.getResolvedIncludes() >> ImmutableSet.copyOf([new ResolvedInclude("MACRO", null)])

        when:
        compiler.handleDiscoveredInputs(spec, compilation, taskInputs)

        then:
        1 * spec.getSourceFiles() >> [ sourceFile ]
        1 * spec.getIncludeRoots() >> includeRoots
        0 * spec._

        2 * taskInputs.newInput(includedFile)
        1 * taskInputs.newInput(notIncludedFile)
        0 * taskInputs._
    }
}
