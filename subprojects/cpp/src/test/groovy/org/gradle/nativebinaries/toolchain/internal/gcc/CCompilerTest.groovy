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

package org.gradle.nativebinaries.toolchain.internal.gcc

import org.gradle.api.Action
import org.gradle.nativebinaries.language.c.internal.CCompileSpec
import org.gradle.nativebinaries.language.c.internal.DefaultCCompileSpec
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    def executable = new File("executable")
    def execActionFactory = Mock(ExecActionFactory)
    Action<List<String>> argAction = Mock(Action)
    CommandLineTool<CCompileSpec> commandLineTool = new CommandLineTool<CCompileSpec>("cCompiler", executable, execActionFactory)
    CCompiler compiler = new CCompiler(commandLineTool, argAction, false);

    def "compiles all source files in separate executions"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")

        def execAction = Mock(ExecAction)

        when:
        CCompileSpec compileSpec = Spy(DefaultCCompileSpec)
        compileSpec.getMacros() >> [foo: "bar", empty: null]
        compileSpec.getObjectFileDir() >> objectFileDir
        compileSpec.getAllArgs() >> ["-firstArg", "-secondArg"]
        compileSpec.getIncludeRoots() >> [testDir.file("include.h")]
        compileSpec.setSourceFiles([testDir.file("one.c"), testDir.file("two.c")])

        and:
        compiler.execute(compileSpec)

        then:
        ["one.c", "two.c"].each{ sourceFileName ->
            1 * argAction.execute([
                    "-x", "c",
                    "-Dfoo=bar", "-Dempty",
                    "-firstArg", "-secondArg",
                    "-c",
                    "-I", testDir.file("include.h").absolutePath,
                    testDir.file(sourceFileName).absolutePath])

            1 * execAction.args([
                    "-x", "c",
                    "-Dfoo=bar", "-Dempty",
                    "-firstArg", "-secondArg",
                    "-c",
                    "-I", testDir.file("include.h").absolutePath,
                    testDir.file(sourceFileName).absolutePath])

        }
        2 * execActionFactory.newExecAction() >> execAction
        2 * execAction.executable(executable)
        2 * execAction.workingDir(_)
        2 * execAction.environment([:])
        2 * execAction.execute()
        0 * execAction._
        0 * argAction._
    }
}
