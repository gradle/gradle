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

import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class DefaultWindowsSdkLocatorTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
        findInPath("rc.exe") >> file("SDK/bin/rc.exe")
    }
    final WindowsSdkLocator windowsSdkLocator = new DefaultWindowsSdkLocator(operatingSystem, windowsRegistry)

    def "locates windows SDK based on executables in path"() {
        when:
        createFile('SDK/bin/rc.exe')
        createFile('SDK/lib/kernel32.lib')

        and:
        def located = windowsSdkLocator.locateWindowsSdks(null)
        def defaultSdk = windowsSdkLocator.getDefaultSdk()

        then:
        located
        defaultSdk.baseDir == file('SDK')
    }

    def file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    def createFile(String name) {
        file(name).createFile()
    }
}
