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
import org.gradle.nativebinaries.language.assembler.internal.AssembleSpec
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AssemblerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    def executable = new File("executable")
    def execActionFactory = Mock(ExecActionFactory)
    Action<List<String>> argAction = Mock(Action)
    CommandLineTool<AssembleSpec> commandLineTool = new CommandLineTool<AssembleSpec>("assembler", executable, execActionFactory)
    Assembler assembler = new Assembler(commandLineTool, argAction);

    def "assembles each source file independently"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def objectFileDir = testDir.file("output/objects")

        def execAction1 = Mock(ExecAction)
        def execAction2 = Mock(ExecAction)

        when:
        AssembleSpec assembleSpec = Mock(AssembleSpec)
        assembleSpec.getObjectFileDir() >> objectFileDir
        assembleSpec.getAllArgs() >> ["-firstArg", "-secondArg"]
        assembleSpec.getSourceFiles() >> [testDir.file("one.s"), testDir.file("two.s")]

        and:
        assembler.execute(assembleSpec)

        then:
        1 * argAction.execute(["-firstArg", "-secondArg", "-o", "one.o", testDir.file("one.s").absolutePath])
        1 * execActionFactory.newExecAction() >> execAction1
        1 * execAction1.executable(executable)
        1 * execAction1.workingDir(objectFileDir)
        1 * execAction1.args(["-firstArg", "-secondArg", "-o", "one.o", testDir.file("one.s").absolutePath])
        1 * execAction1.environment([:])
        1 * execAction1.execute()
        0 * execAction1._

        1 * argAction.execute(["-firstArg", "-secondArg", "-o", "two.o", testDir.file("two.s").absolutePath])
        1 * execActionFactory.newExecAction() >> execAction2
        1 * execAction2.executable(executable)
        1 * execAction2.workingDir(objectFileDir)
        1 * execAction2.args(["-firstArg", "-secondArg", "-o", "two.o", testDir.file("two.s").absolutePath])
        1 * execAction2.environment([:])
        1 * execAction2.execute()
        0 * execAction2._
    }
}
