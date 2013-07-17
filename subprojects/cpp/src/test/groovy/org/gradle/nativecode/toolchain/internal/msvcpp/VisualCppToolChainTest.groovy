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

package org.gradle.nativecode.toolchain.internal.msvcpp
import org.gradle.internal.Factory
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativecode.base.internal.ToolChainAvailability
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class VisualCppToolChainTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final toolChain = new VisualCppToolChain(new OperatingSystem.Windows(), Stub(Factory))


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
        final os = Mock(OperatingSystem)

        when:
        def cppToolChain = new VisualCppToolChain(os, Stub(Factory))

        then:
        os.findInPath("cl.exe") >> file('cl.exe')
        os.findInPath("link.exe") >> file('link.exe')
        os.findInPath("lib.exe") >> file('lib.exe')
        os.findInPath("ml.exe") >> file('ml.exe')

        when:
        def availability = new ToolChainAvailability()
        cppToolChain.checkAvailable(availability)

        then:
        !availability.available
        availability.unavailableMessage == "cl.exe cannot be found"

        when:
        createFile('cl.exe')
        createFile('link.exe')
        createFile('ml.exe')

        and:
        def availability2 = new ToolChainAvailability()
        cppToolChain.checkAvailable(availability2);

        then:
        !availability2.available
        availability2.unavailableMessage == 'lib.exe cannot be found'

        when:
        createFile('lib.exe')

        and:
        def availability3 = new ToolChainAvailability()
        cppToolChain.checkAvailable(availability3);

        then:
        availability3.available
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }
}
