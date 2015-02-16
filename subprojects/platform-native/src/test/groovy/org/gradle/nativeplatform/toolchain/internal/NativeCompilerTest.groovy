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

import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.operations.BuildOperationProcessor
import org.gradle.internal.operations.DefaultBuildOperationProcessor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

public abstract class NativeCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    private static final String O_EXT = ".o"

    protected abstract NativeCompiler getCompiler(CommandLineToolInvocation invocation, String objectFileExtension, boolean useCommandFile)
    protected NativeCompiler getCompiler(CommandLineToolInvocation invocation) {
        getCompiler(invocation, O_EXT, false)
    }

    protected abstract Class<? extends NativeCompileSpec> getCompileSpecType()
    protected abstract List<String> getCompilerSpecificArguments(File includeDir)

    protected CommandLineToolInvocationWorker commandLineTool = Mock(CommandLineToolInvocationWorker)
    protected BuildOperationProcessor buildOperationProcessor = new DefaultBuildOperationProcessor(new DefaultExecutorFactory(), 1)

    def "arguments include source file"() {
        given:
        def invocation = Mock(MutableCommandLineToolInvocation)
        def compiler = getCompiler(invocation)
        def testDir = tmpDirProvider.testDirectory
        def args = []
        def sourceFile = testDir.file("source.ext")

        when:
        compiler.addSourceArgs(args, sourceFile)

        then:
        args == [ sourceFile.absoluteFile.toString() ]
    }

    @Unroll
    def "output file directory honors output extension '#extension' and directory"() {
        given:
        def invocation = Mock(MutableCommandLineToolInvocation)
        def compiler = getCompiler(invocation)
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
        def invocation = new DefaultCommandLineToolInvocation()
        def compiler = getCompiler(invocation)
        def testDir = tmpDirProvider.testDirectory
        def includeDir = testDir.file("includes")
        def expectedArgs = getCompilerSpecificArguments(includeDir)

        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getMacros() >> [foo: "bar", empty: null]
            getAllArgs() >> ["-firstArg", "-secondArg"]
            getIncludeRoots() >> [ includeDir ]
        }

        and:
        def actualArgs = compiler.getArguments(compileSpec)

        then:
        actualArgs == expectedArgs
    }

    @Unroll("Compiles source files (options.txt=#withOptionsFile) with #description")
    def "compiles all source files in separate executions"() {
        given:
        def invocation = new DefaultCommandLineToolInvocation()
        def compiler = getCompiler(invocation, O_EXT, withOptionsFile)
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")
        def sourceFiles = [ testDir.file("source1.ext"), testDir.file("source2.ext") ]

        when:
        def compileSpec = Stub(getCompileSpecType()) {
            getTempDir() >> testDir
            getObjectFileDir() >> objectFileDir
            getSourceFiles() >> sourceFiles
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

    def "base invocation post arg actions run once per execute"() {
        given:
        def invocation = Mock(MutableCommandLineToolInvocation)
        def compiler = getCompiler(invocation)
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")
        def sourceFiles = [ testDir.file("source1.ext"), testDir.file("source2.ext") ]

        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getObjectFileDir() >> objectFileDir
            getSourceFiles() >> sourceFiles
        }

        invocation.copy() >> invocation >> Mock(MutableCommandLineToolInvocation)
        commandLineTool.toRunnableExecution(_) >> { args ->
            def perFileInvocation = args[0]
            return new Runnable() {
                public void run() {
                    perFileInvocation.getArgs()
                }
            }
        }

        and:
        compiler.execute(compileSpec)

        then:
        1 * invocation.getArgs() >> []
    }

    def "invocation for each source file removes post-args actions"() {
        given:
        def invocation = Mock(MutableCommandLineToolInvocation)
        def compiler = getCompiler(invocation)
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")
        def sourceFile = testDir.file("source.ext")

        when:
        compiler.createPerFileInvocation([], sourceFile, objectFileDir)

        then:
        1 * invocation.copy() >> invocation
        1 * invocation.setArgs(_)
        1 * invocation.setWorkDirectory(objectFileDir)
        1 * invocation.clearPostArgsActions()
    }

    def "options file is written"() {
        given:
        def invocation = new DefaultCommandLineToolInvocation()
        def compiler = getCompiler(invocation, O_EXT, true)
        def testDir = tmpDirProvider.testDirectory
        def includeDir = testDir.file("includes")
        def commandLineArgs = getCompilerSpecificArguments(includeDir)

        when:
        NativeCompileSpec compileSpec = Stub(getCompileSpecType()) {
            getMacros() >> [foo: "bar", empty: null]
            getAllArgs() >> ["-firstArg", "-secondArg"]
            getIncludeRoots() >> [ includeDir ]
            getTempDir() >> testDir
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
