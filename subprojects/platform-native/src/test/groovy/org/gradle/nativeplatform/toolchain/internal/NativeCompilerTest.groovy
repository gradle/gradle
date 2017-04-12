/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal

import org.gradle.api.Action
import org.gradle.api.internal.file.BaseDirFileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.operations.BuildOperationProcessor
import org.gradle.internal.operations.DefaultBuildOperationProcessor
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory
import org.gradle.internal.operations.logging.BuildOperationLogger
import org.gradle.internal.progress.TestBuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Executor

abstract class NativeCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    protected CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory = new CompilerOutputFileNamingSchemeFactory(new BaseDirFileResolver(TestFiles.fileSystem(), tmpDirProvider.root, TestFiles.getPatternSetFactory()))
    private static final String O_EXT = ".o"

    protected abstract NativeCompiler getCompiler(CommandLineToolContext invocationContext, String objectFileExtension, boolean useCommandFile)
    protected NativeCompiler getCompiler() {
        getCompiler(new DefaultMutableCommandLineToolContext(), O_EXT, false)
    }

    protected abstract Class<? extends NativeCompileSpec> getCompileSpecType()
    protected abstract List<String> getCompilerSpecificArguments(File includeDir)

    protected CommandLineToolInvocationWorker commandLineTool = Mock(CommandLineToolInvocationWorker)

    WorkerLeaseService workerLeaseService = Stub(WorkerLeaseService)

    protected BuildOperationProcessor buildOperationProcessor = new DefaultBuildOperationProcessor(new TestBuildOperationExecutor(), new DefaultBuildOperationQueueFactory(workerLeaseService), new DefaultExecutorFactory(), 1)

    def setup() {
        _ * workerLeaseService.withLocks(_) >> { args ->
            new Executor() {
                @Override
                void execute(Runnable runnable) {
                    runnable.run()
                }
            }
        }
    }

    def "arguments include source file"() {
        given:
        def compiler = getCompiler()
        def testDir = tmpDirProvider.testDirectory
        def sourceFile = testDir.file("source.ext")

        when:
        def args = compiler.getSourceArgs(sourceFile)

        then:
        args == [ sourceFile.absoluteFile.toString() ]
    }

    @Unroll
    def "output file directory honors output extension '#extension' and directory"() {
        given:
        def compiler = getCompiler()
        def testDir = tmpDirProvider.testDirectory
        def sourceFile = testDir.file("source.ext")

        when:
        def outputFile = compiler.getOutputFileDir(sourceFile, testDir, extension)

        then:
        // Creates directory
        outputFile.parentFile.exists()
        // Rooted under test directory
        outputFile.parentFile.parentFile == testDir
        // TODO: Test for MD5 directory name?
        outputFile.name == "source$extension"

        where:
        extension | _
        ".o"      | _
        ".obj"    | _
    }

    def "arguments contains parameters from spec"() {
        given:
        def compiler = getCompiler()
        def testDir = tmpDirProvider.testDirectory
        def includeDir = testDir.file("includes")
        def expectedArgs = getCompilerSpecificArguments(includeDir)

        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getMacros() >> [foo: "bar", empty: null]
            getAllArgs() >> ["-firstArg", "-secondArg"]
            getIncludeRoots() >> [ includeDir ]
            getOperationLogger() >> Mock(BuildOperationLogger)
            getPrefixHeaderFile() >> null
            getPreCompiledHeaderObjectFile() >> null
        }

        and:
        def actualArgs = compiler.getArguments(compileSpec)

        then:
        actualArgs == expectedArgs
    }

    @Unroll("Compiles source files (options.txt=#withOptionsFile) with #description")
    def "compiles all source files in separate executions"() {
        given:
        def invocationContext = new DefaultMutableCommandLineToolContext()
        def compiler = getCompiler(invocationContext, O_EXT, withOptionsFile)
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")
        def sourceFiles = [ testDir.file("source1.ext"), testDir.file("source2.ext") ]

        when:
        def compileSpec = Stub(getCompileSpecType()) {
            getTempDir() >> testDir
            getObjectFileDir() >> objectFileDir
            getSourceFiles() >> sourceFiles
            getOperationLogger() >> Mock(BuildOperationLogger) {
                getLogLocation() >> "<log location>"
            }
            getPreCompiledHeader() >> null
            getPrefixHeaderFile() >> null
            getPreCompiledHeaderObjectFile() >> null
        }

        and:
        compiler.execute(compileSpec)

        then:

        sourceFiles.each{ sourceFile ->
            1 * commandLineTool.execute(_)
        }
        0 * _

        where:
        withOptionsFile | description
        true            | "options written to options.txt"
        false           | "options passed on the command line only"
    }

    def "user-supplied arg actions run once per execute"() {
        given:
        def invocationContext = new DefaultMutableCommandLineToolContext()
        def action = Mock(Action)
        invocationContext.setArgAction(action)
        def compiler = getCompiler(invocationContext, O_EXT, false)
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")
        def sourceFiles = [ testDir.file("source1.ext"), testDir.file("source2.ext") ]
        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getObjectFileDir() >> objectFileDir
            getSourceFiles() >> sourceFiles
            getOperationLogger() >> Mock(BuildOperationLogger)
            getPreCompiledHeader() >> null
            getPrefixHeaderFile() >> null
            getPreCompiledHeaderObjectFile() >> null
        }
        and:
        invocationContext.getArgAction() >> action

        and:
        compiler.execute(compileSpec)

        then:
        1 * action.execute(_)
        2 * commandLineTool.execute(_)
    }

    def "options file is written"() {
        given:
        def invocationContext = new DefaultMutableCommandLineToolContext()
        def compiler = getCompiler(invocationContext, O_EXT, true)
        def testDir = tmpDirProvider.testDirectory
        def includeDir = testDir.file("includes")
        def commandLineArgs = getCompilerSpecificArguments(includeDir)

        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getMacros() >> [foo: "bar", empty: null]
            getAllArgs() >> ["-firstArg", "-secondArg"]
            getIncludeRoots() >> [ includeDir ]
            getTempDir() >> testDir
            getOperationLogger() >> Mock(BuildOperationLogger)
            getPreCompiledHeader() >> null
            getPrefixHeaderFile() >> null
            getPreCompiledHeaderObjectFile() >> null
        }

        and:
        def actualArgs = compiler.getArguments(compileSpec)

        then:
        // Almost all options are stripped when using the options file
        actualArgs != commandLineArgs
        // options file should exist
        testDir.file("options.txt").exists()
    }
}
