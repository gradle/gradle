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

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.internal.LinkerSpec
import org.gradle.nativebinaries.internal.SharedLibraryLinkerSpec
import org.gradle.nativebinaries.toolchain.internal.CommandLineTool
import org.gradle.nativebinaries.toolchain.internal.MutableCommandLineToolInvocation
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class GccLinkerTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDirProvider = new TestNameTestDirectoryProvider()

    def executable = new File("executable")
    def invocation = Mock(MutableCommandLineToolInvocation)
    CommandLineTool commandLineTool = Mock(CommandLineTool)
    GccLinker linker = new GccLinker(commandLineTool, invocation, false);

    def "compiles all source files in a single execution"() {
        given:
        def testDir = tmpDirProvider.testDirectory
        def outputFile = testDir.file("output/lib")

        final expectedArgs = [
                "-sys1", "-sys2",
                "-shared",
                getSoNameProp("installName"),
                "-o", outputFile.absolutePath,
                testDir.file("one.o").absolutePath,
                testDir.file("two.o").absolutePath,
                "-arg1", "-arg2"].flatten()

        when:
        LinkerSpec spec = Mock(SharedLibraryLinkerSpec)
        spec.getSystemArgs() >> ['-sys1', '-sys2']
        spec.getArgs() >> ['-arg1', '-arg2']
        spec.getOutputFile() >> outputFile
        spec.getLibraries() >> []
        spec.getLibraryPath() >> []
        spec.getInstallName() >> "installName"
        spec.getObjectFiles() >> [testDir.file("one.o"), testDir.file("two.o")]

        and:
        linker.execute(spec)

        then:
        1 * invocation.copy() >> invocation
        1 * invocation.setArgs(expectedArgs)
        1 * commandLineTool.execute(invocation)
        0 * _
    }

    List<String> getSoNameProp(def value) {
        if (OperatingSystem.current().isWindows()) {
            return []
        }
        if (OperatingSystem.current().isMacOsX()) {
            return ["-Wl,-install_name,${value}"]
        }
        return ["-Wl,-soname,${value}"]
    }
}
