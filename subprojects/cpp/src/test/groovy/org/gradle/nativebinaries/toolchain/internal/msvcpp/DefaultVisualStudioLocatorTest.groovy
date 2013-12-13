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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.VersionNumber
import org.junit.Rule

import spock.lang.Specification

class DefaultVisualStudioLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final WindowsRegistry windowsRegistry =  Stub(WindowsRegistry)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
    }
    final VisualStudioLocator visualStudioLocator = new DefaultVisualStudioLocator(operatingSystem, windowsRegistry)

    def "visual studio not found when executables do not exist"() {
        given:
        operatingSystem.findInPath("cl.exe") >> null

        when:
        def located = visualStudioLocator.locateVisualStudioInstalls(null)

        then:
        !located.available
        visualStudioLocator.defaultInstall == null
    }

    def "locates visual studio installation based on executables in path"() {
        def vsDir = vsDir("vs")

        given:
        operatingSystem.findInPath("cl.exe") >> vsDir.file("VC/bin/cl.exe")

        when:
        def located = visualStudioLocator.locateVisualStudioInstalls(null)

        then:
        located.available
        visualStudioLocator.defaultInstall.name == "Path-resolved Visual Studio"
        visualStudioLocator.defaultInstall.version == VersionNumber.UNKNOWN
        visualStudioLocator.defaultInstall.baseDir == vsDir
    }

    def vsDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createFile("VC/bin/cl.exe")
        return dir
    }
}
