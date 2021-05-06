/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.AbstractWindowsKitComponentLocator.PLATFORMS

class DefaultUcrtLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final WindowsComponentLocator ucrtLocator = new DefaultUcrtLocator(windowsRegistry)

    def "uses ucrt found in registry"() {
        def dir1 = ucrtDir("ucrt", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = ucrtLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Universal C Runtime 10"
        result.component.baseDir == dir1
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
    }

    def "uses newest ucrt found in registry"() {
        def dir1 = ucrtDir("ucrt", "10.0.10150.0")
        def dir2 = ucrtDir("ucrt", "10.0.10160.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = ucrtLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Universal C Runtime 10"
        result.component.baseDir == dir2
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10160.0")
    }

    def "handles missing ucrt"() {
        def visitor = new TreeFormatter()

        given:
        windowsRegistry.getStringValue(_, _, _) >> { throw new MissingRegistryEntryException("missing") }

        when:
        def result = ucrtLocator.locateComponent(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        visitor.toString() == "Could not locate a Universal C Runtime installation using the Windows registry."
    }

    def "uses ucrt using specified install dir"() {
        def ucrtDir1 = ucrtDir("ucrt-1", "10.0.10150.1")
        def ucrtDir2 = ucrtDir("ucrt-2", "10.0.10150.2")
        def ignoredDir = ucrtDir("ignored", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> ignoredDir.absolutePath
        assert ucrtLocator.locateComponent(null).available

        when:
        def result = ucrtLocator.locateComponent(ucrtDir1)

        then:
        result.available
        result.component.name == "User-provided Universal C Runtime 10"
        result.component.baseDir == ucrtDir1
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.1")

        when:
        result = ucrtLocator.locateComponent(ucrtDir2)

        then:
        result.available
        result.component.name == "User-provided Universal C Runtime 10"
        result.component.baseDir == ucrtDir2
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.2")
    }

    def "uses ucrt using specified install dir, same as in registry"() {
        def ucrtDir1 = ucrtDir("ucrt-1", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> ucrtDir1.absolutePath
        assert ucrtLocator.locateComponent(null).available

        when:
        def result = ucrtLocator.locateComponent(ucrtDir1)

        then:
        result.available
        result.component.name == "Universal C Runtime 10"
        result.component.baseDir == ucrtDir1
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
    }

    def "ucrt not available when specified install dir does not look like a ucrt"() {
        def ucrtDir1 = tmpDir.createDir("dir")
        def ignoredDir = ucrtDir("ignored", "10.0.10150.0")
        def visitor = Mock(DiagnosticsVisitor)

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> ignoredDir.absolutePath
        assert ucrtLocator.locateComponent(null).available

        when:
        def result = ucrtLocator.locateComponent(ucrtDir1)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("The specified installation directory '${ucrtDir1}' does not appear to contain a Universal C Runtime installation.")
    }

    def "fills in meta-data for ucrt specified by user"() {
        def ucrtDir = ucrtDir("ucrt1", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> ucrtDir.absolutePath

        when:
        def result = ucrtLocator.locateComponent(ucrtDir)

        then:
        result.available
        result.component.name == "Universal C Runtime 10"
        result.component.baseDir == ucrtDir
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
    }

    def ucrtDir(String name, String versionDir) {
        def dir = tmpDir.createDir(name)
        dir.createFile("Include/${versionDir}/ucrt/io.h")
        PLATFORMS.each { dir.createFile("Lib/${versionDir}/ucrt/${it}/libucrt.lib") }
        return dir
    }
}
