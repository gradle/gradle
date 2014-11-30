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

import org.gradle.api.Transformer
import org.gradle.internal.Transformers
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec
import org.gradle.nativeplatform.toolchain.internal.gcc.CCompiler
import org.gradle.nativeplatform.toolchain.internal.gcc.GccOptionsFileArgWriter
import org.gradle.nativeplatform.toolchain.internal.gcc.GccOutputFileArgTransformer
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification

class NativeCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    static class DummyTool extends DefaultCommandLineTool {
        DummyTool() {
            super("dummy", null, null)
        }
        protected void internalExecute(CommandLineToolInvocation invocation) {
            invocation.getArgs()
        }
    }

    CommandLineTool commandLineTool = Spy(DummyTool)

    CommandLineToolInvocation invocation = new DefaultCommandLineToolInvocation()

    ArgsTransformer<CCompileSpec> argsTransformer = new CCompiler.CCompileArgsTransformer()
    Transformer<CCompileSpec, CCompileSpec> specTransformer = Transformers.noOpTransformer()
    OutputFileArgTransformer outputFileArgTransformer = new GccOutputFileArgTransformer()
    OptionsFileArgsWriter argsWriter = Mock(OptionsFileArgsWriter)
    DummyCompiler compiler = new DummyCompiler(commandLineTool, invocation, argsTransformer, specTransformer, outputFileArgTransformer, argsWriter)

    def "capture current behavior of native compiler execute"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")

        when:
        CCompileSpec compileSpec = Stub(CCompileSpec) {
            getMacros() >> [foo: "bar", empty: null]
            getObjectFileDir() >> objectFileDir
            getAllArgs() >> ["-firstArg", "-secondArg"]
            getIncludeRoots() >> [testDir.file("include.h")]
            getSourceFiles() >> [testDir.file("one.c"), testDir.file("two.c")]
        }

        and:
        compiler.execute(compileSpec)

        then:

        2 * commandLineTool.toRunnableExecution(_)
        1 * argsWriter.execute(_)
    }
}
