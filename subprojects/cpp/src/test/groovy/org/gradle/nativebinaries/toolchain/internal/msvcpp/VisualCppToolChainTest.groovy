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

package org.gradle.nativebinaries.toolchain.internal.msvcpp

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.internal.ToolChainAvailability
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class VisualCppToolChainTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final FileResolver fileResolver = Mock(FileResolver)
    final toolChain = new VisualCppToolChain("visualCpp", new OperatingSystem.Windows(), fileResolver, Stub(Factory))


    def "uses .lib file for shared library at link time"() {
        expect:
        toolChain.getSharedLibraryLinkFileName("test") == "test.lib"
        toolChain.getSharedLibraryLinkFileName("test.dll") == "test.lib"
    }

    def "uses .dll file for shared library at runtime time"() {
        expect:
        toolChain.getSharedLibraryName("test") == "test.dll"
        toolChain.getSharedLibraryName("test.dll") == "test.dll"
    }

    def "checks availability of required executables"() {
        final os = Stub(OperatingSystem) {
            isWindows() >> true
            getExecutableName(_ as String) >> { String exeName -> exeName }
            findInPath("cl.exe") >> file('cl.exe')
            findInPath("link.exe") >> file('link.exe')
            findInPath("lib.exe") >> file('lib.exe')
            findInPath("ml.exe") >> file('ml.exe')
        }

        def cppToolChain = new VisualCppToolChain("test", os, fileResolver, Stub(Factory))

        when:
        def availability = new ToolChainAvailability()
        cppToolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == "C++ compiler does not exist (${file("cl.exe")})"

        when:
        createFile('cl.exe')
        createFile('link.exe')
        createFile('ml.exe')

        and:
        def availability2 = new ToolChainAvailability()
        cppToolChain.checkAvailable(availability2);

        then:
        !availability2.available
        availability2.unavailableMessage == "Static library archiver does not exist (${file('lib.exe')})"

        when:
        createFile('lib.exe')

        and:
        def availability3 = new ToolChainAvailability()
        cppToolChain.checkAvailable(availability3);

        then:
        availability3.available
    }

    def "has default tool names"() {
        expect:
        toolChain.cppCompiler.executable == "cl.exe"
        toolChain.CCompiler.executable == "cl.exe"
        toolChain.assembler.executable == "ml.exe"
        toolChain.linker.executable == "link.exe"
        toolChain.staticLibArchiver.executable == "lib.exe"
    }

    def "can update tool names"() {
        when:
        toolChain.assembler.executable = "foo"

        then:
        toolChain.assembler.executable == "foo"
    }

    def "resolves install directory"() {
        when:
        toolChain.installDir = "The Path"

        then:
        fileResolver.resolve("The Path") >> file("one")

        and:
        toolChain.installDir == file("one")
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }
}
