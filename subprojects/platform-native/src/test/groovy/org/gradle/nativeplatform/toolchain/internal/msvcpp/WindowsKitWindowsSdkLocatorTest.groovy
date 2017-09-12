/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import org.gradle.util.VersionNumber
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsKitComponentLocator.PLATFORMS

class WindowsKitWindowsSdkLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final WindowsKitComponentLocator<WindowsKitWindowsSdk> windowsSdkLocator = new WindowsKitWindowsSdkLocator(windowsRegistry)

    def "uses highest Windows Kit version found"() {
        def dir1 = kitDir("sdk1", versions as String[])

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocator.locateComponents(null)

        then:
        result.available
        result.component.name == "Windows SDK 10"
        result.component.version == VersionNumber.withPatchNumber().parse(expected)
        result.component.baseDir == dir1
        result.component.includeDirs as Set == [ dir1.file("Include/${expected}/um"), dir1.file("Include/${expected}/shared") ] as Set

        where:
        versions                                           | expected
        [ "10.0.10150.0" ]                                 | "10.0.10150.0"
        [ "10.0.10150.0", "10.0.10240.0", "10.0.10500.0" ] | "10.0.10500.0"
        [ "10.0.10240.0", "10.0.10500.0", "10.1.10080.0" ] | "10.1.10080.0"
    }

    def "SDK not available when not found in registry"() {
        def visitor = Mock(TreeVisitor)

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> { throw new MissingRegistryEntryException("missing") }

        when:
        def result = windowsSdkLocator.locateComponents(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate a Windows SDK installation using the Windows registry.")
    }

    def "SDK not available when registry dir contains no version directories"() {
        def visitor = Mock(TreeVisitor)
        def dir1 = kitDir("dir1")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocator.locateComponents(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate a Windows SDK installation using the Windows registry.")
    }

    def "SDK not available when registry dir does not contain any versions that look like an SDK"() {
        def visitor = Mock(TreeVisitor)
        def dir1 = badKitDir("sdk1", "10.0.10240.0", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocator.locateComponents(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate a Windows SDK installation using the Windows registry.")
    }

    def "does not use registry dir versions that do not look like an SDK"() {
        def dir1 = kitDir("sdk1", "10.0.10150.0")
        def dir2 = badKitDir("sdk1", "10.0.10240.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocator.locateComponents(null)

        then:
        result.available
        result.component.name == "Windows SDK 10"
        result.component.baseDir == dir1
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
        result.component.includeDirs as Set == [ dir2.file("Include/10.0.10150.0/um"), dir2.file("Include/10.0.10150.0/shared") ] as Set
    }

    def "uses windows SDK using specified install dir"() {
        def dir1 = kitDir("sdk1", "10.0.10240.0")
        def dir2 = kitDir("sdk2", "10.0.10150.0")
        def dir3 = kitDir("sdk3", "10.0.10500.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath
        assert windowsSdkLocator.locateComponents(null).available

        when:
        def result = windowsSdkLocator.locateComponents(dir2)

        then:
        result.available
        result.component.name == "User-provided Windows SDK 10"
        result.component.baseDir == dir2
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
        result.component.includeDirs as Set == [ dir2.file("Include/10.0.10150.0/um"), dir2.file("Include/10.0.10150.0/shared") ] as Set

        when:
        result = windowsSdkLocator.locateComponents(dir3)

        then:
        result.available
        result.component.name == "User-provided Windows SDK 10"
        result.component.baseDir == dir3
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10500.0")
        result.component.includeDirs as Set == [ dir3.file("Include/10.0.10500.0/um"), dir3.file("Include/10.0.10500.0/shared") ] as Set
    }

    def "SDK not available when specified install dir does not look like an SDK"() {
        def visitor = Mock(TreeVisitor)
        def dir1 = kitDir("sdk1", "10.0.10240.0")
        def dir2 = badKitDir("sdk2", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath
        assert windowsSdkLocator.locateComponents(null).available

        when:
        def result = windowsSdkLocator.locateComponents(dir2)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("The specified installation directory '$dir2' does not appear to contain a Windows SDK installation.")
    }

    def kitDir(String name, String... versions) {
        def dir = tmpDir.createDir(name)
        PLATFORMS.each { dir.createFile("bin/${it}/rc.exe") }
        versions.each { version ->
            dir.createFile("Include/${version}/um/windows.h")
            PLATFORMS.each { dir.createFile("Lib/${version}/um/${it}/kernel32.lib") }
        }
        return dir
    }

    def badKitDir(String name, String... versions) {
        def dir = tmpDir.createDir(name)
        PLATFORMS.each { dir.createFile("bin/${it}/rc.exe") }
        versions.each { version ->
            dir.createFile("Include/${version}/um/foo.h")
            PLATFORMS.each { dir.createFile("Lib/${version}/um/${it}/bar.lib") }
        }
        return dir
    }
}
