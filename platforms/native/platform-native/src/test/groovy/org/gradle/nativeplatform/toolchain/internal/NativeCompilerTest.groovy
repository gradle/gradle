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
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationExecutorSupport
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationProgressEventListenerAdapter
import org.gradle.internal.operations.logging.BuildOperationLogger
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.internal.time.Clock
import org.gradle.internal.work.DefaultWorkerLimits
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.Executor

abstract class NativeCompilerTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider(getClass())

    protected CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory = new CompilerOutputFileNamingSchemeFactory(TestFiles.resolver(tmpDirProvider.testDirectory))
    private static final String O_EXT = ".o"

    protected abstract NativeCompiler getCompiler(CommandLineToolContext invocationContext, String objectFileExtension, boolean useCommandFile)

    protected NativeCompiler getCompiler() {
        getCompiler(new DefaultMutableCommandLineToolContext(), O_EXT, false)
    }

    protected abstract Class<? extends NativeCompileSpec> getCompileSpecType()

    protected abstract List<String> getCompilerSpecificArguments(File includeDir, File systemIncludeDir)

    protected CommandLineToolInvocationWorker commandLineTool = Mock(CommandLineToolInvocationWorker)

    WorkerLeaseService workerLeaseService = new TestWorkerLeaseService()

    protected final BuildOperationListener buildOperationListener = Mock(BuildOperationListener)
    protected final Clock timeProvider = Mock(Clock)
    protected BuildOperationExecutor buildOperationExecutor = BuildOperationExecutorSupport.builder(new DefaultWorkerLimits(DefaultParallelismConfiguration.getDefaultMaxWorkerCount()))
        .withWorkerLeaseService(workerLeaseService)
        .withTimeSupplier(timeProvider)
        .withExecutionListenerFactory { new BuildOperationProgressEventListenerAdapter(buildOperationListener, new NoOpProgressLoggerFactory(), timeProvider) }
        .build()

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
        args == [sourceFile.absoluteFile.toString()]
    }

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
        def systemIncludeDir = testDir.file("system")
        def expectedArgs = getCompilerSpecificArguments(includeDir, systemIncludeDir)

        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getMacros() >> [foo: "bar", empty: null]
            getAllArgs() >> ["-firstArg", "-secondArg"]
            getIncludeRoots() >> [includeDir]
            getSystemIncludeRoots() >> [systemIncludeDir]
            getOperationLogger() >> Mock(BuildOperationLogger)
            getPrefixHeaderFile() >> null
            getPreCompiledHeaderObjectFile() >> null
        }

        and:
        def actualArgs = compiler.getArguments(compileSpec)

        then:
        actualArgs == expectedArgs
    }

    def "Compiles source files (options.txt=#withOptionsFile) with #description"() {
        given:
        def invocationContext = new DefaultMutableCommandLineToolContext()
        def compiler = getCompiler(invocationContext, O_EXT, withOptionsFile)
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")
        def sourceFiles = [testDir.file("source1.ext"), testDir.file("source2.ext")]

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

        sourceFiles.each { sourceFile ->
            1 * commandLineTool.execute(_, _)
        }
        4 * timeProvider.getCurrentTime()
        2 * buildOperationListener.started(_, _)
        2 * buildOperationListener.finished(_, _)
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
        def sourceFiles = [testDir.file("source1.ext"), testDir.file("source2.ext")]
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
        2 * commandLineTool.execute(_, _)
    }

    def "options file is written"() {
        given:
        def invocationContext = new DefaultMutableCommandLineToolContext()
        def compiler = getCompiler(invocationContext, O_EXT, true)
        def testDir = tmpDirProvider.testDirectory
        def includeDir = testDir.file("includes")
        def systemIncludeDir = testDir.file("system")
        def commandLineArgs = getCompilerSpecificArguments(includeDir, systemIncludeDir)

        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getMacros() >> [foo: "bar", empty: null]
            getAllArgs() >> ["-firstArg", "-secondArg"]
            getIncludeRoots() >> [includeDir]
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
