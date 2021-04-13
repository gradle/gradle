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

package org.gradle.nativeplatform.toolchain.internal.msvcpp

import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification

class LegacyWindowsSdkLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final OperatingSystem operatingSystem = Stub(OperatingSystem) {
        isWindows() >> true
        getExecutableName(_ as String) >> { String exeName -> exeName }
    }
    final WindowsSdkLocator windowsSdkLocator = new LegacyWindowsSdkLocator(operatingSystem, windowsRegistry)

    def "uses highest version SDK found in registry"() {
        def dir1 = sdkDir("sdk1")
        def dir2 = sdkDir("sdk2")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1", "v2"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> dir1.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "sdk 1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v2/, "InstallationFolder") >> dir2.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v2/, "ProductVersion") >> "7.1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v2/, "ProductName") >> "sdk 2"

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "sdk 2"
        result.component.version == VersionNumber.parse("7.1")
        result.component.baseDir == dir2
    }

    def "uses windows kit if version is higher than windows SDK"() {
        def dir1 = sdkDir("sdk1")
        def dir2 = kitDir("sdk2")
        def dir3 = kitDir("sdk3")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> dir1.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "sdk 1"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot") >> dir2.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot81") >> dir3.absolutePath

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Windows Kit 8.1"
        result.component.version == VersionNumber.parse("8.1")
        result.component.baseDir == dir3
    }

    def "handles missing SDKs and Kits"() {
        def dir = sdkDir("sdk1")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\SDKs\Windows/) >> { throw new MissingRegistryEntryException("missing") }
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot") >> { throw new MissingRegistryEntryException("missing") }
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot81") >> dir.absolutePath

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Windows Kit 8.1"
        result.component.version == VersionNumber.parse("8.1")
        result.component.baseDir == dir
    }

    def "locates windows SDK based on executables in path"() {
        def sdkDir = sdkDir("sdk")

        given:
        operatingSystem.findInPath("rc.exe") >> sdkDir.file("bin/rc.exe")

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Path-resolved Windows SDK"
        result.component.version == VersionNumber.UNKNOWN
        result.component.baseDir == sdkDir
    }

    def "SDK not available when not found in registry or system path"() {
        def visitor = Mock(DiagnosticsVisitor)

        given:
        operatingSystem.findInPath(_) >> null

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate a Windows SDK installation, using the Windows registry and system path.")
    }

    def "uses windows SDK using specified install dir"() {
        def sdkDir1 = this.sdkDir("sdk-1")
        def sdkDir2 = this.sdkDir("sdk-2")
        def ignoredDir = sdkDir("ignored")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> ignoredDir.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "installed sdk"
        assert windowsSdkLocator.locateComponent(null).available

        when:
        def result = windowsSdkLocator.locateComponent(sdkDir1)

        then:
        result.available
        result.component.name == "User-provided Windows SDK"
        result.component.version == VersionNumber.UNKNOWN
        result.component.baseDir == sdkDir1

        when:
        result = windowsSdkLocator.locateComponent(sdkDir2)

        then:
        result.available
        result.component.name == "User-provided Windows SDK"
        result.component.version == VersionNumber.UNKNOWN
        result.component.baseDir == sdkDir2
    }

    def "SDK not available when specified install dir does not look like an SDK"() {
        def sdkDir1 = tmpDir.createDir("dir")
        def ignoredDir = sdkDir("ignored")
        def visitor = Mock(DiagnosticsVisitor)

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> ignoredDir.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "installed sdk"
        assert windowsSdkLocator.locateComponent(null).available

        when:
        def result = windowsSdkLocator.locateComponent(sdkDir1)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("The specified installation directory '$sdkDir1' does not appear to contain a Windows SDK installation.")
    }

    def "fills in meta-data from registry for SDK discovered using the path"() {
        def sdkDir = sdkDir("sdk1")

        given:
        operatingSystem.findInPath("rc.exe") >> sdkDir.file("bin/rc.exe")

        and:
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> sdkDir.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "installed sdk"

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "installed sdk"
        result.component.version == VersionNumber.parse("7.0")
        result.component.baseDir == sdkDir
    }

    def "fills in meta-data from registry for SDK specified by user"() {
        def sdkDir = sdkDir("sdk1")

        given:
        operatingSystem.findInPath(_) >> null

        and:
        windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows/) >> ["v1"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "InstallationFolder") >> sdkDir.absolutePath
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductVersion") >> "7.0"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Microsoft SDKs\Windows\v1/, "ProductName") >> "installed sdk"

        when:
        def result = windowsSdkLocator.locateComponent(sdkDir)

        then:
        result.available
        result.component.name == "installed sdk"
        result.component.version == VersionNumber.parse("7.0")
        result.component.baseDir == sdkDir
    }

    def sdkDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createFile("bin/rc.exe")
        dir.createFile("lib/kernel32.lib")
        return dir
    }

    def kitDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createFile("bin/x86/rc.exe")
        dir.createFile("lib/win8/um/x86/kernel32.lib")
        return dir
    }
}
