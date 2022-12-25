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

package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.VersionNumber
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate.Compatibility.LEGACY
import static org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER


class VisualStudioVersionDeterminerTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def commandLineLocator = Mock(VisualStudioVersionLocator)
    def windowsRegistryLocator = Mock(VisualStudioVersionLocator)
    def visualCppMetadataProvider = Mock(VisualCppMetadataProvider)
    def determiner = new VisualStudioVersionDeterminer(commandLineLocator, windowsRegistryLocator, visualCppMetadataProvider)
    List<VisualStudioInstallCandidate> vswhereInstalls = []
    List<VisualStudioInstallCandidate> windowsRegistryInstalls = []

    def "can determine a VS2017 version of an install from command line"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir2, "14.0")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromInstallDir(dir1)

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()

        and:
        metadata.version == VersionNumber.parse("15.3.26730.16")
        metadata.installDir == dir1
        metadata.visualCppDir == new File(dir1, "VC/Tools/MSVC/1.2.3.4")
        metadata.visualCppVersion == VersionNumber.parse("1.2.3.4")
        metadata.compatibility == VS2017_OR_LATER
    }

    def "can determine a legacy version of an install from command line"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir2, "14.0")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromInstallDir(dir2)

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()

        and:
        metadata.version == VersionNumber.parse("14.0")
        metadata.installDir == dir2
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.parse("14.0")
        metadata.compatibility == LEGACY
    }

    def "can determine legacy version of an install from windows registry when command line has no results"() {
        def dir1 = tmpDir.createDir("dir2")
        def dir2 = tmpDir.createDir("dir3")

        given:
        windowsRegistryInstall(dir1, "14.0")
        windowsRegistryInstall(dir2, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromInstallDir(dir2)

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> windowsRegistryInstalls

        and:
        metadata.installDir == dir2
        metadata.compatibility == LEGACY
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.parse("12.0")
        metadata.version == VersionNumber.parse("12.0")
    }

    def "can determine legacy metadata from installation directory when not found in command line results"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromInstallDir(dir2)

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> null

        and:
        metadata.installDir == dir2
        metadata.compatibility == LEGACY
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.UNKNOWN
        metadata.version == VersionNumber.UNKNOWN
    }

    def "can determine VS2017 metadata from installation directory when not found in command line results"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromInstallDir(dir2)

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> visualCppMetadata(dir2)

        and:
        metadata.installDir == dir2
        metadata.compatibility == VS2017_OR_LATER
        metadata.visualCppDir == new File(dir2, "VC/Tools/MSVC/1.2.3.4")
        metadata.visualCppVersion == VersionNumber.parse("1.2.3.4")
        metadata.version == VersionNumber.UNKNOWN
    }

    def "can determine metadata from installation directory when command line has no results and not found in registry"() {
        def dir1 = tmpDir.createDir("dir2")
        def dir2 = tmpDir.createDir("dir3")

        given:
        windowsRegistryInstall(dir1, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromInstallDir(dir2)

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> windowsRegistryInstalls
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> null

        and:
        metadata.installDir == dir2
        metadata.compatibility == LEGACY
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.UNKNOWN
        metadata.version == VersionNumber.UNKNOWN
    }

    def "can determine VS2017 metadata from installation directory when command line has no results and not found in registry"() {
        def dir1 = tmpDir.createDir("dir2")
        def dir2 = tmpDir.createDir("dir3")

        given:
        windowsRegistryInstall(dir1, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromInstallDir(dir2)

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> windowsRegistryInstalls
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> visualCppMetadata(dir2)

        and:
        metadata.installDir == dir2
        metadata.compatibility == VS2017_OR_LATER
        metadata.visualCppDir == new File(dir2, "VC/Tools/MSVC/1.2.3.4")
        metadata.visualCppVersion == VersionNumber.parse("1.2.3.4")
        metadata.version == VersionNumber.UNKNOWN
    }

    def "can determine legacy metadata from compiler path and command line"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir2, "14.0")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromCompiler(new File(dir2, "VC/bin/cl.exe"))

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()

        and:
        metadata.version == VersionNumber.parse("14.0")
        metadata.installDir == dir2
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.parse("14.0")
        metadata.compatibility == LEGACY
    }

    def "can determine VS2017 metadata from compiler path and command line"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir2, "14.0")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromCompiler(new File(dir1, "VC/Tools/MSVC/1.2.3.4/bin/HostX86/x86/cl.exe"))

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()

        and:
        metadata.version == VersionNumber.parse("15.3.26730.16")
        metadata.installDir == dir1
        metadata.visualCppDir == new File(dir1, "VC/Tools/MSVC/1.2.3.4")
        metadata.visualCppVersion == VersionNumber.parse("1.2.3.4")
        metadata.compatibility == VS2017_OR_LATER
    }

    def "can determine legacy version from compiler path (#platform) and windows registry when command line has no results"() {
        def dir1 = tmpDir.createDir("dir2")
        def dir2 = tmpDir.createDir("dir3")

        given:
        windowsRegistryInstall(dir1, "14.0")
        windowsRegistryInstall(dir2, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromCompiler(new File(dir2, compilerPath))

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> windowsRegistryInstalls

        and:
        metadata.installDir == dir2
        metadata.compatibility == LEGACY
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.parse("12.0")
        metadata.version == VersionNumber.parse("12.0")

        where:
        compilerPath              | platform
        "VC/bin/cl.exe"           | "x86"
        "VC/bin/x86_amd64/cl.exe" | "x64"
    }

    def "can determine legacy metadata from compiler path when not found in command line results"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromCompiler(new File(dir2, "VC/bin/cl.exe"))

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> null

        and:
        metadata.installDir == dir2
        metadata.compatibility == LEGACY
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.UNKNOWN
        metadata.version == VersionNumber.UNKNOWN
    }

    def "can determine VS2017 metadata from compiler path when not found in command line results"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def dir3 = tmpDir.createDir("dir3")

        given:
        vswhereInstall(dir1, "15.3.26730.16")
        vswhereInstall(dir3, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromCompiler(new File(dir2, "VC/Tools/MSVC/1.2.3.4/bin/HostX86/x86/cl.exe"))

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> vswhereInstalls
        0 * windowsRegistryLocator.getVisualStudioInstalls()
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> visualCppMetadata(dir2)

        and:
        metadata.installDir == dir2
        metadata.compatibility == VS2017_OR_LATER
        metadata.visualCppDir == new File(dir2, "VC/Tools/MSVC/1.2.3.4")
        metadata.visualCppVersion == VersionNumber.parse("1.2.3.4")
        metadata.version == VersionNumber.UNKNOWN
    }

    def "can determine legacy metadata from compiler path (#platform) when command line has no results and not found in registry"() {
        def dir1 = tmpDir.createDir("dir2")
        def dir2 = tmpDir.createDir("dir3")

        given:
        windowsRegistryInstall(dir1, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromCompiler(new File(dir2, compilerPath))

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> windowsRegistryInstalls
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> null

        and:
        metadata.installDir == dir2
        metadata.compatibility == LEGACY
        metadata.visualCppDir == new File(dir2, "VC")
        metadata.visualCppVersion == VersionNumber.UNKNOWN
        metadata.version == VersionNumber.UNKNOWN

        where:
        compilerPath              | platform
        "VC/bin/cl.exe"           | "x86"
        "VC/bin/x86_amd64/cl.exe" | "x64"
    }

    def "can determine VS2017 metadata from compiler path when command line has no results and not found in registry"() {
        def dir1 = tmpDir.createDir("dir2")
        def dir2 = tmpDir.createDir("dir3")

        given:
        windowsRegistryInstall(dir1, "12.0")

        when:
        VisualStudioInstallCandidate metadata = determiner.getVisualStudioMetadataFromCompiler(new File(dir2, "VC/Tools/MSVC/1.2.3.4/bin/HostX86/x86/cl.exe"))

        then:
        1 * commandLineLocator.getVisualStudioInstalls() >> []
        1 * windowsRegistryLocator.getVisualStudioInstalls() >> windowsRegistryInstalls
        1 * visualCppMetadataProvider.getVisualCppFromMetadataFile(_) >> visualCppMetadata(dir2)

        and:
        metadata.installDir == dir2
        metadata.compatibility == VS2017_OR_LATER
        metadata.visualCppDir == new File(dir2, "VC/Tools/MSVC/1.2.3.4")
        metadata.visualCppVersion == VersionNumber.parse("1.2.3.4")
        metadata.version == VersionNumber.UNKNOWN
    }

    void vswhereInstall(File dir, String version) {
        VersionNumber versionNumber = VersionNumber.parse(version)
        vswhereInstalls << new VisualStudioMetadataBuilder()
            .installDir(dir)
            .version(versionNumber)
            .visualCppDir(new File(dir, versionNumber.getMajor() >= 15 ? "VC/Tools/MSVC/1.2.3.4" : "VC"))
            .visualCppVersion(versionNumber.getMajor() >= 15 ? VersionNumber.parse("1.2.3.4") : versionNumber)
            .build()
    }

    void windowsRegistryInstall(File dir, String version) {
        windowsRegistryInstalls << new VisualStudioMetadataBuilder()
            .installDir(dir)
            .version(VersionNumber.parse(version))
            .visualCppDir(new File(dir, "VC"))
            .visualCppVersion(VersionNumber.parse(version))
            .build()
    }

    VisualCppInstallCandidate visualCppMetadata(File dir) {
        return new VisualCppInstallCandidate() {
            @Override
            File getVisualCppDir() {
                return new File(dir, "VC/Tools/MSVC/1.2.3.4")
            }

            @Override
            VersionNumber getVersion() {
                return VersionNumber.parse("1.2.3.4")
            }
        }
    }
}
