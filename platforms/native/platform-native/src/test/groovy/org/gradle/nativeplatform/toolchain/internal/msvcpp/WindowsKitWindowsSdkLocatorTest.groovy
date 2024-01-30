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
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.TextUtil
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.AbstractWindowsKitComponentLocator.PLATFORMS

class WindowsKitWindowsSdkLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final WindowsRegistry windowsRegistry = Stub(WindowsRegistry)
    final SystemInfo systemInfo = Stub(SystemInfo) {
        getArchitecture() >> SystemInfo.Architecture.amd64
    }
    final WindowsComponentLocator<WindowsKitSdkInstall> windowsSdkLocator = new WindowsKitWindowsSdkLocator(windowsRegistry, systemInfo)

    def "cross compile uses host kit for resources"() {
        def dir1 = kitDirWithVersionedBinDir("sdk1", ["10.0.10150.0", "10.0.10500.0"] as String[])

        given:
        WindowsRegistry windowsRegistryLocal = Stub(WindowsRegistry)
        SystemInfo systemInfoLocal = Stub(SystemInfo) {
            getArchitecture() >> SystemInfo.Architecture.amd64
        }
        WindowsComponentLocator<WindowsKitSdkInstall> windowsSdkLocatorLocal = new WindowsKitWindowsSdkLocator(windowsRegistryLocal, systemInfoLocal)

        windowsRegistryLocal.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocatorLocal.locateComponent(null)

        then:
        result.available
        def platformSdk = result.component.forPlatform(arm64())
        platformSdk.path == [dir1.file("bin/10.0.10500.0/arm64")]
        platformSdk.resourceCompiler == dir1.file("bin/10.0.10500.0/x64/rc.exe")
    }

    def "host x64 compile uses host kit for resources"() {
        def dir1 = kitDirWithVersionedBinDir("sdk1", ["10.0.10150.0", "10.0.10500.0"] as String[])

        given:
        WindowsRegistry windowsRegistryLocal = Stub(WindowsRegistry)
        SystemInfo systemInfoLocal = Stub(SystemInfo) {
            getArchitecture() >> SystemInfo.Architecture.amd64
        }
        WindowsComponentLocator<WindowsKitSdkInstall> windowsSdkLocatorLocal = new WindowsKitWindowsSdkLocator(windowsRegistryLocal, systemInfoLocal)

        windowsRegistryLocal.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocatorLocal.locateComponent(null)

        then:
        result.available
        def platformSdk = result.component.forPlatform(x64())
        platformSdk.path == [dir1.file("bin/10.0.10500.0/x64")]
        platformSdk.resourceCompiler == dir1.file("bin/10.0.10500.0/x64/rc.exe")
    }

    def "host arm64 compile uses host kit for resources"() {
        def dir1 = kitDirWithVersionedBinDir("sdk1", ["10.0.10150.0", "10.0.10500.0"] as String[])

        given:
        WindowsRegistry windowsRegistryLocal = Stub(WindowsRegistry)
        SystemInfo systemInfoLocal = Stub(SystemInfo) {
            getArchitecture() >> SystemInfo.Architecture.aarch64
        }
        WindowsComponentLocator<WindowsKitSdkInstall> windowsSdkLocatorLocal = new WindowsKitWindowsSdkLocator(windowsRegistryLocal, systemInfoLocal)

        windowsRegistryLocal.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocatorLocal.locateComponent(null)

        then:
        result.available
        def platformSdk = result.component.forPlatform(arm64())
        platformSdk.path == [dir1.file("bin/10.0.10500.0/arm64")]
        platformSdk.resourceCompiler == dir1.file("bin/10.0.10500.0/arm64/rc.exe")
    }

    def "host x86 compile uses host kit for resources"() {
        def dir1 = kitDirWithVersionedBinDir("sdk1", ["10.0.10150.0", "10.0.10500.0"] as String[])

        given:
        WindowsRegistry windowsRegistryLocal = Stub(WindowsRegistry)
        SystemInfo systemInfoLocal = Stub(SystemInfo) {
            getArchitecture() >> SystemInfo.Architecture.i386
        }
        WindowsComponentLocator<WindowsKitSdkInstall> windowsSdkLocatorLocal = new WindowsKitWindowsSdkLocator(windowsRegistryLocal, systemInfoLocal)

        windowsRegistryLocal.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocatorLocal.locateComponent(null)

        then:
        result.available
        def platformSdk = result.component.forPlatform(x86())
        platformSdk.path == [dir1.file("bin/10.0.10500.0/x86")]
        platformSdk.resourceCompiler == dir1.file("bin/10.0.10500.0/x86/rc.exe")
    }

    def "uses highest Windows Kit version found"() {
        def dir1 = kitDir("sdk1", versions as String[])

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Windows SDK 10"
        result.component.version == VersionNumber.withPatchNumber().parse(expected)
        result.component.baseDir == dir1
        def platformSdk = result.component.forPlatform(x64())
        platformSdk.includeDirs == [ dir1.file("Include/${expected}/um"), dir1.file("Include/${expected}/shared") ]
        platformSdk.path == [dir1.file("bin/x64")]
        platformSdk.libDirs == [dir1.file("Lib/${expected}/um/x64")]

        where:
        versions                                           | expected
        [ "10.0.10150.0" ]                                 | "10.0.10150.0"
        [ "10.0.10150.0", "10.0.10240.0", "10.0.10500.0" ] | "10.0.10500.0"
        [ "10.0.10240.0", "10.0.10500.0", "10.1.10080.0" ] | "10.1.10080.0"
    }

    def "locates Windows Kit with versioned bin dir"() {
        def dir1 = kitDirWithVersionedBinDir("sdk1", ["10.0.10150.0", "10.0.10500.0"] as String[])

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Windows SDK 10"
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10500.0")
        result.component.baseDir == dir1
        def platformSdk = result.component.forPlatform(x64())
        platformSdk.includeDirs == [ dir1.file("Include/10.0.10500.0/um"), dir1.file("Include/10.0.10500.0/shared") ]
        platformSdk.path == [dir1.file("bin/10.0.10500.0/x64")]
        platformSdk.libDirs == [dir1.file("Lib/10.0.10500.0/um/x64")]
    }

    def "SDK not available when not found in registry"() {
        def visitor = new TreeFormatter()

        given:
        windowsRegistry.getStringValue(_, _, _) >> { throw new MissingRegistryEntryException("missing") }

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        visitor.toString() == "Could not locate a Windows SDK installation using the Windows registry."
    }

    def "SDK not available when registry dir contains no version directories"() {
        def visitor = new TreeFormatter()
        def dir1 = kitDir("dir1")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath
        windowsRegistry.getStringValue(_, _, _) >> { throw new MissingRegistryEntryException("missing") }

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        visitor.toString() == TextUtil.toPlatformLineSeparators("""Could not locate a Windows SDK installation. None of the following locations contain a valid installation:
  - ${dir1}""")
    }

    def "SDK not available when registry dir does not contain any versions that look like an SDK"() {
        def visitor = new TreeFormatter()
        def dir1 = badKitDir("sdk1", "10.0.10240.0", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath
        windowsRegistry.getStringValue(_, _, _) >> { throw new MissingRegistryEntryException("missing") }

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        visitor.toString() == TextUtil.toPlatformLineSeparators("""Could not locate a Windows SDK installation. None of the following locations contain a valid installation:
  - ${dir1}""")
    }

    def "does not use registry dir versions that do not look like an SDK"() {
        def dir1 = kitDir("sdk1", "10.0.10150.0")
        def dir2 = badKitDir("sdk1", "10.0.10240.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath

        when:
        def result = windowsSdkLocator.locateComponent(null)

        then:
        result.available
        result.component.name == "Windows SDK 10"
        result.component.baseDir == dir1
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
        def platformSdk = result.component.forPlatform(x64())
        platformSdk.includeDirs == [ dir2.file("Include/10.0.10150.0/um"), dir2.file("Include/10.0.10150.0/shared") ]
    }

    def "uses windows SDK using specified install dir"() {
        def dir1 = kitDir("sdk1", "10.0.10240.0")
        def dir2 = kitDir("sdk2", "10.0.10150.0")
        def dir3 = kitDir("sdk3", "10.0.10500.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath
        assert windowsSdkLocator.locateComponent(null).available

        when:
        def result = windowsSdkLocator.locateComponent(dir2)

        then:
        result.available
        result.component.name == "User-provided Windows SDK 10"
        result.component.baseDir == dir2
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
        def platformSdk = result.component.forPlatform(x64())
        platformSdk.includeDirs == [ dir2.file("Include/10.0.10150.0/um"), dir2.file("Include/10.0.10150.0/shared") ]
        platformSdk.path == [dir2.file("bin/x64")]
        platformSdk.libDirs == [dir2.file("Lib/10.0.10150.0/um/x64")]

        when:
        result = windowsSdkLocator.locateComponent(dir3)
        platformSdk = result.component.forPlatform(x64())

        then:
        result.available
        result.component.name == "User-provided Windows SDK 10"
        result.component.baseDir == dir3
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10500.0")
        platformSdk.includeDirs == [ dir3.file("Include/10.0.10500.0/um"), dir3.file("Include/10.0.10500.0/shared") ]
        platformSdk.path == [dir3.file("bin/x64")]
        platformSdk.libDirs == [dir3.file("Lib/10.0.10500.0/um/x64")]
    }

    def "uses windows SDK with versioned bin dir using specified install dir"() {
        def dir1 = kitDirWithVersionedBinDir("sdk1", "10.0.10240.0")
        def dir2 = kitDirWithVersionedBinDir("sdk2", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath
        assert windowsSdkLocator.locateComponent(null).available

        when:
        def result = windowsSdkLocator.locateComponent(dir2)

        then:
        result.available
        result.component.name == "User-provided Windows SDK 10"
        result.component.baseDir == dir2
        result.component.version == VersionNumber.withPatchNumber().parse("10.0.10150.0")
        def platformSdk = result.component.forPlatform(x64())
        platformSdk.includeDirs == [ dir2.file("Include/10.0.10150.0/um"), dir2.file("Include/10.0.10150.0/shared") ]
        platformSdk.path == [dir2.file("bin/10.0.10150.0/x64")]
        platformSdk.libDirs == [dir2.file("Lib/10.0.10150.0/um/x64")]
    }

    def "SDK not available when specified install dir does not look like an SDK"() {
        def visitor = new TreeFormatter()
        def dir1 = kitDir("sdk1", "10.0.10240.0")
        def dir2 = badKitDir("sdk2", "10.0.10150.0")

        given:
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\Windows Kits\Installed Roots/, "KitsRoot10") >> dir1.absolutePath
        assert windowsSdkLocator.locateComponent(null).available

        when:
        def result = windowsSdkLocator.locateComponent(dir2)

        then:
        !result.available
        result.component == null

        when:
        result.explain(visitor)

        then:
        visitor.toString() == "The specified installation directory '$dir2' does not appear to contain a Windows SDK installation."
    }

    def arm64() {
        def platform = Stub(NativePlatformInternal)
        def architecture = Stub(ArchitectureInternal)
        platform.architecture >> architecture
        architecture.isArm64() >> true
        platform
    }

    def x64() {
        def platform = Stub(NativePlatformInternal)
        def architecture = Stub(ArchitectureInternal)
        platform.architecture >> architecture
        architecture.isAmd64() >> true
        platform
    }

    def x86() {
        def platform = Stub(NativePlatformInternal)
        def architecture = Stub(ArchitectureInternal)
        platform.architecture >> architecture
        architecture.isI386() >> true
        platform
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

    def kitDirWithVersionedBinDir(String name, String... versions) {
        def dir = tmpDir.createDir(name)
        versions.each { version ->
            PLATFORMS.each { dir.createFile("bin/${version}/${it}/rc.exe") }
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
