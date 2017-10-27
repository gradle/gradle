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

import net.rubygrapefruit.platform.SystemInfo

import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetadata
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetadataBuilder
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionLocator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import org.gradle.util.VersionNumber
import org.junit.Rule

import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.ArchitectureDescriptorBuilder.*

class DefaultVisualStudioLocatorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final VisualStudioVersionLocator commandLineLocator = Mock(VisualStudioVersionLocator)
    final VisualStudioVersionLocator windowsRegistryLocator = Mock(VisualStudioVersionLocator)
    final VisualStudioVersionLocator systemPathLocator = Mock(VisualStudioVersionLocator)
    final VisualStudioMetaDataProvider versionDeterminer = Mock(VisualStudioMetaDataProvider)
    final SystemInfo systemInfo =  Stub(SystemInfo)
    final VisualStudioLocator visualStudioLocator = new DefaultVisualStudioLocator(commandLineLocator, windowsRegistryLocator, systemPathLocator, versionDeterminer, systemInfo)

    def "use highest visual studio version found in the registry"() {
        def dir1 = vsDir("vs1")
        def dir2 = vsDir("vs2")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> { [legacyVsInstall(dir1, "11.0"), legacyVsInstall(dir2, "12.0")] }
        0 * systemPathLocator.getVisualStudioInstalls()

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        result.available
        result.visualStudio.name == "Visual Studio 12.0.0"
        result.visualStudio.version == VersionNumber.parse("12.0")
        result.visualStudio.baseDir == dir2
        result.visualStudio.visualCpp.name == "Visual C++ 12.0.0"
        result.visualStudio.visualCpp.version == VersionNumber.parse("12.0")
    }

    def "use highest visual studio version found from the command line"() {
        def dir1 = vsDir("vs1")
        def dir2 = vsDir("vs2")
        def dir3 = vs2017Dir("vs3")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> { [vs2017Install(dir3, "15.0"), legacyVsInstall(dir1, "11.0"), legacyVsInstall(dir2, "12.0")] }
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        0 * systemPathLocator.getVisualStudioInstalls()

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        result.available
        result.visualStudio.name == "Visual Studio 15.0.0"
        result.visualStudio.version == VersionNumber.parse("15.0")
        result.visualStudio.baseDir == dir3
        result.visualStudio.visualCpp.name == "Visual C++ 1.2.3-4"
        result.visualStudio.visualCpp.version == VersionNumber.parse("1.2.3.4")
    }

    def "can locate all versions of visual studio using registry"() {
        def dir1 = vsDir("vs1")
        def dir2 = vsDir("vs2")
        def dir3 = vsDir("vs3")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> { [legacyVsInstall(dir1, "11.0"), legacyVsInstall(dir2, "12.0"), legacyVsInstall(dir3, "13.0")] }
        0 * systemPathLocator.getVisualStudioInstalls()

        when:
        def allResults = visualStudioLocator.locateAllVisualStudioVersions()

        then:
        allResults.size() == 3
        allResults.collect { it.visualStudio.visualCpp.name } == [ "Visual C++ 13.0.0", "Visual C++ 12.0.0", "Visual C++ 11.0.0" ]
        allResults.every { it.available }
    }

    def "can locate all versions of visual studio using command line"() {
        def dir1 = vsDir("vs1")
        def dir2 = vsDir("vs2")
        def dir3 = vsDir("vs3")
        def dir4 = vs2017Dir("vs4")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> { [vs2017Install(dir4, "15.0"), legacyVsInstall(dir1, "11.0"), legacyVsInstall(dir2, "12.0"), legacyVsInstall(dir3, "13.0")] }
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        0 * systemPathLocator.getVisualStudioInstalls()

        when:
        def allResults = visualStudioLocator.locateAllVisualStudioVersions()

        then:
        allResults.size() == 4
        allResults.collect { it.visualStudio.visualCpp.name } == [ "Visual C++ 1.2.3-4", "Visual C++ 13.0.0", "Visual C++ 12.0.0", "Visual C++ 11.0.0" ]
        allResults.every { it.available }
    }

    def "visual studio not available when nothing in registry or command line and executable not found in path"() {
        def visitor = Mock(TreeVisitor)

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> []
        1 * systemPathLocator.getVisualStudioInstalls() >> []

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        !result.available
        result.visualStudio == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate a Visual Studio installation, using the command line tool, Windows registry or system path.")
    }

    def "visual studio not available when locating all versions and nothing in registry or command line and executable not found in path"() {
        def visitor = Mock(TreeVisitor)

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> []
        1 * systemPathLocator.getVisualStudioInstalls() >> []

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

    def "locates visual studio 2017 installation based on executables in path"() {
        def vsDir = vs2017Dir("vs")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> []
        1 * systemPathLocator.getVisualStudioInstalls() >> [vs2017Install(vsDir, null)]
        1 * systemPathLocator.getSource() >> "system path"

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        result.available
        result.visualStudio.name == "Visual Studio from system path"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir
        result.visualStudio.visualCpp.name == "Visual C++ 1.2.3-4"
        result.visualStudio.visualCpp.version == VersionNumber.parse("1.2.3.4")
    }

    def "uses legacy visual studio using specified install dir"() {
        def vsDir1 = vsDir("vs")
        def vsDir2 = vsDir("vs-2")
        def ignored = vsDir("vs-3")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> { [legacyVsInstall(ignored, "12.0")] }
        0 * systemPathLocator.getVisualStudioInstalls()
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(vsDir1) >> legacyVsInstall(vsDir1, "11.0")
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(vsDir2) >> legacyVsInstall(vsDir2, "14.0")
        assert visualStudioLocator.locateDefaultVisualStudioInstall().available

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir1)

        then:
        result.available
        result.visualStudio.name == "Visual Studio 11.0.0"
        result.visualStudio.version == VersionNumber.parse("11.0")
        result.visualStudio.baseDir == vsDir1

        when:
        result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir2)

        then:
        result.available
        result.visualStudio.name == "Visual Studio 14.0.0"
        result.visualStudio.version == VersionNumber.parse("14.0")
        result.visualStudio.baseDir == vsDir2
    }

    def "uses visual studio 2017 using specified install dir"() {
        def vsDir1 = vs2017Dir("vs")
        def vsDir2 = vs2017Dir("vs-2")
        def ignored = vs2017Dir("vs-3")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> { [vs2017Install(ignored, "15.0")]}
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        0 * systemPathLocator.getVisualStudioInstalls()
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(vsDir1) >> vs2017Install(vsDir1, "15.1")
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(vsDir2) >> vs2017Install(vsDir2, "15.2")
        assert visualStudioLocator.locateDefaultVisualStudioInstall().available

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir1)

        then:
        result.available
        result.visualStudio.name == "Visual Studio 15.1.0"
        result.visualStudio.version == VersionNumber.parse("15.1")
        result.visualStudio.baseDir == vsDir1
        result.visualStudio.visualCpp.version == VersionNumber.parse("1.2.3.4")

        when:
        result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir2)

        then:
        result.available
        result.visualStudio.name == "Visual Studio 15.2.0"
        result.visualStudio.version == VersionNumber.parse("15.2")
        result.visualStudio.baseDir == vsDir2
        result.visualStudio.visualCpp.version == VersionNumber.parse("1.2.3.4")
    }

    def "uses version of visual studio 2017 using specified install dir when visual cpp version can be determined"() {
        def vsDir1 = vs2017Dir("vs")
        def vsDir2 = vs2017Dir("vs-2")
        def ignored = vs2017Dir("vs-3")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> { [vs2017Install(ignored, "15.0")]}
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        0 * systemPathLocator.getVisualStudioInstalls()
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(vsDir1) >> vs2017Install(vsDir1, null)
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(vsDir2) >> vs2017Install(vsDir2, null)
        assert visualStudioLocator.locateDefaultVisualStudioInstall().available

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir1)

        then:
        result.available
        result.visualStudio.name == "Visual Studio from user provided path"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir1
        result.visualStudio.visualCpp.version == VersionNumber.parse("1.2.3.4")

        when:
        result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir2)

        then:
        result.available
        result.visualStudio.name == "Visual Studio from user provided path"
        result.visualStudio.version == VersionNumber.UNKNOWN
        result.visualStudio.baseDir == vsDir2
        result.visualStudio.visualCpp.version == VersionNumber.parse("1.2.3.4")
    }

    def "visual studio not found when specified directory does not look like an install"() {
        def visitor = Mock(TreeVisitor)
        def providedDir = tmpDir.createDir("vs")
        def ignored = vsDir("vs-2")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> { [legacyVsInstall(ignored, "12.0")] }
        0 * systemPathLocator.getVisualStudioInstalls()
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

    def "visual studio not found when visual cpp version is unknown"() {
        def visitor = Mock(TreeVisitor)
        def ignored = vsDir("vs-2")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> []
        1 * systemPathLocator.getVisualStudioInstalls() >> { [legacyVsInstall(ignored, null)] }

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall()

        then:
        !result.available
        result.visualStudio == null

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not locate a Visual Studio installation, using the command line tool, Windows registry or system path.")
    }

    def "visual studio not found when version cannot be determined for specified directory"() {
        def visitor = Mock(TreeVisitor)
        def providedDir = vsDir("vs")
        def ignored = vsDir("vs-2")

        given:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> { [legacyVsInstall(ignored, "12.0")] }
        0 * systemPathLocator.getVisualStudioInstalls()
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(_) >> legacyVsInstall(providedDir, null)
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

    def "fills in meta-data for user specified install"() {
        def vsDir = vsDir("vs")

        given:
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(_) >> legacyVsInstall(vsDir, "12.0")
        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir)

        then:
        result.available
        result.visualStudio.name == "Visual Studio 12.0.0"
        result.visualStudio.version == VersionNumber.parse("12.0")
        result.visualStudio.baseDir == vsDir
        result.visualStudio.visualCpp.name == "Visual C++ 12.0.0"
        result.visualStudio.visualCpp.version == VersionNumber.parse("12.0")
    }

    @Unroll
    def "finds correct legacy paths for #targetPlatform on #os operating system (64-bit install: #is64BitInstall)"() {
        def vsDir = fullVsDir("vs", is64BitInstall)
        def vcDir = new File(vsDir, "VC")

        given:
        systemInfo.getArchitecture() >> systemArchitecture
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(_) >> legacyVsInstall(vsDir, "12.0")

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir)

        then:
        result.visualStudio.visualCpp.getCompiler(platform(targetPlatform)) == new File(expectedBuilder.getBinPath(vcDir), "cl.exe")
        result.visualStudio.visualCpp.getLibraryPath(platform(targetPlatform)) == expectedBuilder.getLibPath(vcDir)
        result.visualStudio.visualCpp.getAssembler(platform(targetPlatform)) == new File(expectedBuilder.getBinPath(vcDir), expectedBuilder.asmFilename)

        where:
        os       | systemArchitecture            | targetPlatform | is64BitInstall | expectedBuilder
        "32-bit" | SystemInfo.Architecture.i386  | "amd64"        | false          | LEGACY_AMD64_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "amd64"        | true           | LEGACY_AMD64_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "amd64"        | false          | LEGACY_AMD64_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "x86"          | false          | LEGACY_X86_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "x86"          | true           | LEGACY_X86_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "x86"          | false          | LEGACY_X86_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "ia64"         | false          | LEGACY_IA64_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "ia64"         | true           | LEGACY_IA64_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "ia64"         | false          | LEGACY_IA64_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "arm"          | false          | LEGACY_ARM_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "arm"          | true           | LEGACY_ARM_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "arm"          | false          | LEGACY_ARM_ON_X86
    }

    @Unroll
    def "finds correct VS2017 paths for #targetPlatform on #os operating system (64-bit install: #is64BitInstall)"() {
        def vsDir = fullVs2017Dir("vs", is64BitInstall)
        def vcDir = new File(vsDir, "VC/Tools/MSVC/1.2.3.4")

        given:
        systemInfo.getArchitecture() >> systemArchitecture
        1 * versionDeterminer.getVisualStudioMetadataFromInstallDir(_) >> vs2017Install(vsDir, "15.0")

        when:
        def result = visualStudioLocator.locateDefaultVisualStudioInstall(vsDir)

        then:
        result.visualStudio.visualCpp.getCompiler(platform(targetPlatform)) == new File(expectedBuilder.getBinPath(vcDir), "cl.exe")
        result.visualStudio.visualCpp.getLibraryPath(platform(targetPlatform)) == expectedBuilder.getLibPath(vcDir)
        result.visualStudio.visualCpp.getAssembler(platform(targetPlatform)) == new File(expectedBuilder.getBinPath(vcDir), expectedBuilder.asmFilename)

        where:
        os       | systemArchitecture            | targetPlatform | is64BitInstall | expectedBuilder
        "32-bit" | SystemInfo.Architecture.i386  | "amd64"        | false          | AMD64_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "amd64"        | true           | AMD64_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "amd64"        | false          | AMD64_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "x86"          | false          | X86_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "x86"          | true           | X86_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "x86"          | false          | X86_ON_X86

        "32-bit" | SystemInfo.Architecture.i386  | "arm"          | false          | ARM_ON_X86
        "64-bit" | SystemInfo.Architecture.amd64 | "arm"          | true           | ARM_ON_AMD64
        "64-bit" | SystemInfo.Architecture.amd64 | "arm"          | false          | ARM_ON_X86
    }

    def vs2017Dir(String name) {
        def dir = tmpDir.createDir(name)
        dir.createDir("Common7")
        dir.createFile("VC/Tools/MSVC/1.2.3.4/bin/HostX86/x86/cl.exe")
        dir.createFile("VC/Auxiliary/Build/Microsoft.VCToolsVersion.default.txt").text = "1.2.3.4"
        return dir
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
        createCompilers(vcDir, is64BitInstall) { it in [
            LEGACY_AMD64_ON_X86,
            LEGACY_AMD64_ON_AMD64,
            LEGACY_AMD64_ON_X86,
            LEGACY_X86_ON_X86,
            LEGACY_X86_ON_AMD64,
            LEGACY_X86_ON_X86,
            LEGACY_IA64_ON_X86,
            LEGACY_IA64_ON_X86,
            LEGACY_IA64_ON_X86,
            LEGACY_ARM_ON_X86,
            LEGACY_ARM_ON_AMD64,
            LEGACY_ARM_ON_X86
        ]}
        return dir
    }

    def fullVs2017Dir(String name, boolean is64BitInstall) {
        def dir = vs2017Dir(name)
        def vcDir = new File(dir, "VC/Tools/MSVC/1.2.3.4")
        createCompilers(vcDir, is64BitInstall) { it in [
            AMD64_ON_X86,
            AMD64_ON_AMD64,
            AMD64_ON_X86,
            X86_ON_X86,
            X86_ON_AMD64,
            X86_ON_X86,
            ARM_ON_X86,
            ARM_ON_AMD64,
            ARM_ON_X86
        ]}
        return dir
    }

    def createCompilers(File vcDir, boolean is64BitInstall, Closure condition) {
        for (ArchitectureDescriptorBuilder builder : values().findAll(condition)) {
            if (requires64BitInstall(builder) && !is64BitInstall) {
                continue;
            }

            builder.getBinPath(vcDir).mkdirs()
            builder.getLibPath(vcDir).mkdirs()
            new File(builder.getBinPath(vcDir), "cl.exe").createNewFile()
        }
    }

    boolean requires64BitInstall(ArchitectureDescriptorBuilder builders) {
        return builders in [
            LEGACY_AMD64_ON_AMD64,
            LEGACY_X86_ON_AMD64,
            LEGACY_ARM_ON_AMD64,
            AMD64_ON_AMD64,
            X86_ON_AMD64,
            ARM_ON_AMD64
        ]
    }

    def platform(String name) {
        return Stub(NativePlatformInternal) {
            getArchitecture() >> {
                Architectures.forInput(name)
            }
        }
    }

    VisualStudioMetadata legacyVsInstall(File installDir, String version) {
        return new VisualStudioMetadataBuilder()
            .installDir(installDir)
            .visualCppDir(new File(installDir, "VC"))
            .visualCppVersion(VersionNumber.parse(version))
            .version(VersionNumber.parse(version))
            .build()
    }

    VisualStudioMetadata vs2017Install(File installDir, String version) {
        return new VisualStudioMetadataBuilder()
            .installDir(installDir)
            .visualCppDir(new File(installDir, "VC/Tools/MSVC/1.2.3.4"))
            .visualCppVersion(VersionNumber.parse("1.2.3.4"))
            .version(VersionNumber.parse(version))
            .compatibility(VisualStudioMetadata.Compatibility.VS2017_OR_LATER)
            .build()
    }
}
