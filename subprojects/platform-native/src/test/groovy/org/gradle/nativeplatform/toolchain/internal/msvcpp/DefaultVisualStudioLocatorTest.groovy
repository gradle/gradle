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
import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry

import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultVisualStudioLocator.ArchitectureDescriptorBuilder
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import org.gradle.util.VersionNumber
import org.junit.Rule

import spock.lang.Specification
import spock.lang.Unroll

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
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["", "11.0", "12.0", "ignore-me"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "11.0") >> dir1.absolutePath + "/VC"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> dir2.absolutePath + "/VC"

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        result.available
        result.visualStudio.name == "Visual C++ 12.0"
        result.visualStudio.version == VersionNumber.parse("12.0")
        result.visualStudio.baseDir == dir2
        result.visualStudio.visualCpp
    }

    def "can locate all versions of visual studio"() {
        def dir1 = vsDir("vs1");
        def dir2 = vsDir("vs2");
        def dir3 = vsDir("vs3")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["", "11.0", "12.0", "13.0", "ignore-me"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "11.0") >> dir1.absolutePath + "/VC"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> dir2.absolutePath + "/VC"
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "13.0") >> dir3.absolutePath + "/VC"

        when:
        def allResults = visualStudioLocator.locateAllVisualStudioVersions()

        then:
        allResults.size() == 3
        allResults.collect { it.visualStudio.name } == [ "Visual C++ 13.0", "Visual C++ 12.0", "Visual C++ 11.0" ]
        allResults.every { it.available }
    }

    def "visual studio not available when nothing in registry and executable not found in path"() {
        def visitor = Mock(TreeVisitor)

        given:
        windowsRegistry.getValueNames(_, _) >> { throw new MissingRegistryEntryException("not found") }
        operatingSystem.findInPath(_) >> null

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        !result.available
        result.visualStudio == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate a Visual Studio installation, using the Windows registry and system path.")
    }

    def "visual studio not available when locating all versions and nothing in registry and executable not found in path"() {
        def visitor = Mock(TreeVisitor)

        given:
        windowsRegistry.getValueNames(_, _) >> { throw new MissingRegistryEntryException("not found") }
        operatingSystem.findInPath(_) >> null

        when:
        def allResults = visualStudioLocator.locateAllVisualStudioVersions()

        then:
        allResults.size() == 1
        !allResults.get(0).available
        allResults.get(0).visualStudio == null

        when:
        allResults.get(0).explain(visitor)

        then:
        1 * visitor.node("Could not locate a Visual Studio installation, using the Windows registry and system path.")
    }

    def "locates visual studio installation based on executables in path"() {
        def vsDir = vsDir("vs")

        given:
        windowsRegistry.getValueNames(_, _) >> { throw new MissingRegistryEntryException("not found") }
        operatingSystem.findInPath("cl.exe") >> vsDir.file("VC/bin/cl.exe")

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        result.available
        result.visualStudio.name == "Visual C++ from system path"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir
    }

    def "locates visual studio installation based on executables in path when locating all versions"() {
        def dir1 = vsDir("vs1");
        def dir2 = vsDir("vs2");

        given:
        operatingSystem.findInPath("cl.exe") >> dir2.file("VC/bin/cl.exe")
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["", "11.0", "ignore-me"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "11.0") >> dir1.absolutePath + "/VC"

        when:
        def allResults = visualStudioLocator.locateAllVisualStudioVersions()

        then:
        allResults.size() == 2
        allResults.every { it.available }
        allResults.collect { it.visualStudio.name } == [ "Visual C++ 11.0", "Visual C++ from system path" ]
    }

    def "uses visual studio using specified install dir"() {
        def vsDir1 = vsDir("vs")
        def vsDir2 = vsDir("vs-2")
        def ignored = vsDir("vs-3")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["12.0"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> ignored.absolutePath + "/VC"
        assert visualStudioLocator.locateDefaultVisualStudioInstall().available

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir1)

        then:
        result.available
        result.visualStudio.name == "Visual C++ from user provided path"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir1

        when:
        result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir2)

        then:
        result.available
        result.visualStudio.name == "Visual C++ from user provided path"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir2
    }

    def "visual studio not found when specified directory does not look like an install"() {
        def visitor = Mock(TreeVisitor)
        def providedDir = tmpDir.createDir("vs")
        def ignoredDir = vsDir("vs-2")

        given:
        operatingSystem.findInPath(_) >> null
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["12.0"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> ignoredDir.absolutePath + "/VC"
        assert visualStudioLocator.locateDefaultVisualStudioInstall().available

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(providedDir)

        then:
        !result.available
        result.visualStudio == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("The specified installation directory '$providedDir' does not appear to contain a Visual Studio installation.")
    }

    def "fills in meta-data from registry for install discovered using the system path"() {
        def vsDir = vsDir("vs")

        given:
        operatingSystem.findInPath("cl.exe") >> vsDir.file("VC/bin/cl.exe")

        and:
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["12.0"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> vsDir.absolutePath + "/VC"

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        result.available
        result.visualStudio.name == "Visual C++ 12.0"
        result.visualStudio.version == VersionNumber.parse("12.0")
        result.visualStudio.baseDir == vsDir
    }

    def "fills in meta-data from registry for user specified install"() {
        def vsDir = vsDir("vs")

        given:
        operatingSystem.findInPath(_) >> null

        and:
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["12.0"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> vsDir.absolutePath + "/VC"

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir)

        then:
        result.available
        result.visualStudio.name == "Visual C++ 12.0"
        result.visualStudio.version == VersionNumber.parse("12.0")
        result.visualStudio.baseDir == vsDir
    }

    @Unroll
    def "finds correct paths for #targetPlatform on #os operating system (64-bit install: #is64BitInstall)"() {
        def vsDir = fullVsDir("vs", is64BitInstall)
        def vcDir = new File(vsDir, "VC")

        given:
        operatingSystem.findInPath(_) >> null
        systemInfo.getArchitecture() >> systemArchitecture

        and:
        windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/) >> ["12.0"]
        windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, /SOFTWARE\Microsoft\VisualStudio\SxS\VC7/, "12.0") >> vsDir.absolutePath + "/VC"

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir)

        then:
        result.visualStudio.visualCpp.getCompiler(platform(targetPlatform)) == new File(expectedBuilder.getBinPath(vcDir), "cl.exe")
        result.visualStudio.visualCpp.getLibraryPath(platform(targetPlatform)) == expectedBuilder.getLibPath(vcDir)
        result.visualStudio.visualCpp.getAssembler(platform(targetPlatform)) == new File(expectedBuilder.getBinPath(vcDir), expectedBuilder.asmFilename)

        where:
        os       | systemArchitecture            | targetPlatform | is64BitInstall | expectedBuilder
        "32-bit" | SystemInfo.Architecture.i386  | "amd64"        | false          | ArchitectureDescriptorBuilder.AMD64_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "amd64"        | true           | ArchitectureDescriptorBuilder.AMD64_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "amd64"        | false          | ArchitectureDescriptorBuilder.AMD64_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "x86"          | false          | ArchitectureDescriptorBuilder.X86_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "x86"          | true           | ArchitectureDescriptorBuilder.X86_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "x86"          | false          | ArchitectureDescriptorBuilder.X86_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "ia64"         | false          | ArchitectureDescriptorBuilder.IA64_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "ia64"         | true           | ArchitectureDescriptorBuilder.IA64_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "ia64"         | false          | ArchitectureDescriptorBuilder.IA64_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "arm"          | false          | ArchitectureDescriptorBuilder.ARM_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "arm"          | true           | ArchitectureDescriptorBuilder.ARM_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "arm"          | false          | ArchitectureDescriptorBuilder.ARM_ON_X86
    }

    def vsDir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createDir("Common7")
        dir.createFile("VC/bin/cl.exe")
        dir.createDir("VC/lib")
        return dir
    }

    def fullVsDir(String name, boolean is64BitInstall) {
        def dir = vsDir(name)
        def vcDir = new File(dir, "VC")
        for (ArchitectureDescriptorBuilder builder : ArchitectureDescriptorBuilder.values()) {
            if (requires64BitInstall(builder) && !is64BitInstall) {
                continue;
            }

            builder.getBinPath(vcDir).mkdirs()
            builder.getLibPath(vcDir).mkdirs()
            new File(builder.getBinPath(vcDir), "cl.exe").createNewFile()
        }
        return dir
    }

    boolean requires64BitInstall(ArchitectureDescriptorBuilder builders) {
        return builders in [ ArchitectureDescriptorBuilder.AMD64_ON_AMD64, ArchitectureDescriptorBuilder.X86_ON_AMD64, ArchitectureDescriptorBuilder.ARM_ON_AMD64 ]
    }

    def platform(String name) {
        return Stub(NativePlatformInternal) {
            getArchitecture() >> {
                Architectures.forInput(name)
            }
        }
    }
}
