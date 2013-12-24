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

import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry

import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.VersionNumber
import org.junit.Rule

import spock.lang.Specification

class DefaultVisualStudioLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final WindowsRegistry windowsRegistry =  Stub(WindowsRegistry)
    final SystemInfo systemInfo =  Stub(SystemInfo)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
    }
    final VisualStudioLocator visualStudioLocator = new DefaultVisualStudioLocator(operatingSystem, windowsRegistry, systemInfo)

    def "use highest visual studio version found in the registry"() {
        def dir1 = vsDir("vs1");
        def dir2 = vsDir("vs2");

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VS7/) >> ["", "11.0", "12.0"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VS7/, "11.0") >> dir1.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VS7/, "12.0") >> dir2.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "11.0") >> dir1.absolutePath + "/VC"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> dir2.absolutePath + "/VC"

        when:
        def result = visualStudioLocator.locateVisualStudioInstalls(null)

        then:
        result.available
        result.visualStudio.name == "Visual Studio 12.0"
        result.visualStudio.version == VersionNumber.parse("12.0")
        result.visualStudio.baseDir == dir2
        result.visualStudio.visualCpp
    }

    def "visual studio not found when executables do not exist"() {
        given:
        operatingSystem.findInPath(_) >> null

        when:
        def result = visualStudioLocator.locateVisualStudioInstalls(null)

        then:
        !result.available
        result.visualStudio == null
    }

    def "locates visual studio installation based on executables in path"() {
        def vsDir = vsDir("vs")

        given:
        operatingSystem.findInPath("cl.exe") >> vsDir.file("VC/bin/cl.exe")

        when:
        def result = visualStudioLocator.locateVisualStudioInstalls(null)

        then:
        result.available
        result.visualStudio.name == "Path-resolved Visual Studio"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir
    }

    def "uses visual studio using specified install dir"() {
        def vsDir = vsDir("vs")

        given:
        operatingSystem.findInPath(_) >> null

        when:
        def result = visualStudioLocator.locateVisualStudioInstalls(vsDir)

        then:
        result.available
        result.visualStudio.name == "User-provided Visual Studio"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir
    }

    def "fills in meta-data from registry for install discovered using the path"() {
        def vsDir = vsDir("vs")

        given:
        operatingSystem.findInPath("cl.exe") >> vsDir.file("VC/bin/cl.exe")

        and:
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VS7/) >> ["12.0"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VS7/, "12.0") >> vsDir.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> vsDir.absolutePath + "/VC"
        
        when:
        def result = visualStudioLocator.locateVisualStudioInstalls(null)

        then:
        result.available
        result.visualStudio.name == "Visual Studio 12.0"
        result.visualStudio.version == VersionNumber.parse("12.0")
        result.visualStudio.baseDir == vsDir
    }

    def vsDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createDir("Common7")
        dir.createFile("VC/bin/cl.exe")
        dir.createDir("VC/lib")
        return dir
    }
}
