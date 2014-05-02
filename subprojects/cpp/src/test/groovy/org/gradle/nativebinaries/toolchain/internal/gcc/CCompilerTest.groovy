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

import org.gradle.internal.hash.HashUtil
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.language.c.internal.CCompileSpec
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool
import org.gradle.nativebinaries.toolchain.internal.MutableCommandLineToolInvocation
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CCompilerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    def executable = new File("executable")
    def invocation = Mock(MutableCommandLineToolInvocation)
    CommandLineTool commandLineTool = Mock(CommandLineTool)
    String objectFileExtension = OperatingSystem.current().isWindows() ? ".obj" : ".o";
    CCompiler compiler = new CCompiler(commandLineTool, invocation, objectFileExtension, false);

    def "compiles all source files in separate executions"() {
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
        1 * invocation.copy() >> invocation
        1 * invocation.setWorkDirectory(objectFileDir)

        ["one.c", "two.c"].each{ sourceFileName ->

            TestFile sourceFile = testDir.file(sourceFileName)
            String objectOutputPath = outputFilePathFor(objectFileDir, sourceFile)

            1 * invocation.setArgs([
                    "-x", "c",
                    "-Dfoo=bar", "-Dempty",
                    "-firstArg", "-secondArg",
                    "-c",
                    "-I", testDir.file("include.h").absolutePath,
                    testDir.file(sourceFileName).absolutePath,
                    "-o", objectOutputPath])
            1 * commandLineTool.execute(invocation)
        }
        0 * _
    }

    String outputFilePathFor(File objectFileRoot, TestFile testFile) {
        String relativeObjectFilePath = "${HashUtil.createCompactMD5(testFile.absolutePath)}/${testFile.name - ".c"}$objectFileExtension"
        String outputFilePath = new File(objectFileRoot, relativeObjectFilePath).absolutePath;
        outputFilePath
    }
}
