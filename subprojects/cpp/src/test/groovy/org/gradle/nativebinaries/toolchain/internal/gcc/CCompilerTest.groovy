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
import org.gradle.internal.hash.HashUtil
import org.gradle.nativebinaries.language.c.internal.CCompileSpec
import org.gradle.nativebinaries.language.c.internal.DefaultCCompileSpec
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import org.gradle.internal.os.OperatingSystem

class CCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    def executable = new File("executable")
    def execActionFactory = Mock(ExecActionFactory)
    Action<List<String>> argAction = Mock(Action)
    CommandLineTool commandLineTool = new CommandLineTool("cCompiler", executable, execActionFactory)
    CCompiler compiler = new CCompiler(commandLineTool, argAction, false);
    String objectFileExtension = OperatingSystem.current().isWindows() ? ".obj" : ".o";

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
        2 * argAction.execute([
                "-x", "c",
                "-Dfoo=bar", "-Dempty",
                "-firstArg", "-secondArg",
                "-c",
                "-I", testDir.file("include.h").absolutePath])

        ["one.c", "two.c"].each{ sourceFileName ->

            TestFile sourceFile = testDir.file(sourceFileName)
            String objectOutputPath = outputFilePathFor(objectFileDir, sourceFile)

            1 * execAction.args([
                    "-x", "c",
                    "-Dfoo=bar", "-Dempty",
                    "-firstArg", "-secondArg",
                    "-c",
                    "-I", testDir.file("include.h").absolutePath,
                    testDir.file(sourceFileName).absolutePath,
                    "-o", objectOutputPath])

        }
        2 * execActionFactory.newExecAction() >> execAction
        2 * execAction.executable(executable)
        2 * execAction.workingDir(_)
        2 * execAction.environment([:])
        2 * execAction.execute()
        0 * execAction._
        0 * argAction._
    }

    String outputFilePathFor(File objectFileRoot, TestFile testFile) {
        String relativeObjectFilePath = "${HashUtil.createCompactMD5(testFile.absolutePath)}/${testFile.name - ".c"}$objectFileExtension"
        String outputFilePath = new File(objectFileRoot, relativeObjectFilePath).absolutePath;
        outputFilePath
    }
}
