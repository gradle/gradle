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

package org.gradle.ide.visualstudio.internal
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.language.base.LanguageSourceSet
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.DefaultFlavor
import org.gradle.nativebinaries.internal.ProjectNativeBinaryInternal
import org.gradle.nativebinaries.internal.ProjectNativeComponentInternal
import org.gradle.nativebinaries.platform.Architecture
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.platform.PlatformContainer
import org.gradle.nativebinaries.platform.internal.ArchitectureNotationParser
import spock.lang.Specification

class VisualStudioProjectMapperTest extends Specification {
    def flavors = Mock(FlavorContainer)
    def platforms = Mock(PlatformContainer)
    def mapper = new VisualStudioProjectMapper(flavors, platforms)

    def executable = Mock(ExecutableInternal)
    ExecutableBinaryInternal executableBinary
    ExecutableBinaryInternal executableBinary2
    def library = Mock(LibraryInternal)

    def flavorOne = Mock(Flavor)
    def buildTypeOne = Mock(BuildType)
    def buildTypeTwo = Mock(BuildType)
    def platformOne = Mock(Platform)

    def sourceOne = Mock(LanguageSourceSet)
    def extraSource = Mock(LanguageSourceSet)

    def setup() {
        executableBinary = createExecutableBinary("exeBinaryName", buildTypeOne, platformOne, [sourceOne])
        executableBinary2 = createExecutableBinary("exeBinaryTwoName", buildTypeTwo, platformOne, [sourceOne])

        executable.source >> source([sourceOne])
        executable.baseName >> "exeName"
        library.baseName >> "libName"

        flavorOne.name >> "flavorOne"
        buildTypeOne.name >> "buildTypeOne"
        buildTypeTwo.name >> "buildTypeTwo"
        platformOne.name >> "platformOne"
        platformOne.architecture >> arch("i386")
    }

    def "maps executable binary to visual studio project"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne]
        executable.getBinaries() >> new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [executableBinary])

        then:
        checkNames executableBinary, "exeNameExe", 'buildTypeOne', 'Win32'
    }

    def "maps library binary types to visual studio projects"() {
        when:
        def sharedLibraryBinary = libraryBinary(SharedLibraryBinaryInternal)
        def staticLibraryBinary = libraryBinary(StaticLibraryBinaryInternal)

        library.chooseFlavors(flavors) >> [flavorOne]
        library.choosePlatforms(platforms) >> [platformOne]
        library.source >> source([sourceOne])
        library.getBinaries() >> new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [sharedLibraryBinary, staticLibraryBinary])

        then:
        checkNames sharedLibraryBinary, "libNameDll", 'buildTypeOne', 'Win32'
        checkNames staticLibraryBinary, "libNameLib", 'buildTypeOne', 'Win32'
    }

    def "maps different configuration names for native binaries that have different build types"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne]
        executable.getBinaries() >> new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [executableBinary, executableBinary2])

        then:
        checkNames executableBinary, "exeNameExe", "buildTypeOne", "Win32"
        checkNames executableBinary2, "exeNameExe", "buildTypeTwo", "Win32"
    }

    def "maps different project names for native component that have multiple flavors"() {
        when:
        1 * executable.chooseFlavors(flavors) >> [flavorOne, new DefaultFlavor("flavorTwo")]
        executable.choosePlatforms(platforms) >> [platformOne]
        executable.getBinaries() >> new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [executableBinary, executableBinary2])

        then:
        checkNames executableBinary, "flavorOneExeNameExe", 'buildTypeOne', 'Win32'
    }

    def "maps different project names for native binaries that have different sources for platform"() {
        when:
        def platformTwo = Mock(Platform)
        platformTwo.name >> "platformTwo"
        platformTwo.architecture >> arch("i386")

        def executableBinaryExtra = createExecutableBinary("exeBinaryExtra", buildTypeOne, platformTwo, [sourceOne, extraSource])
        def executableBinaryExtra2 = createExecutableBinary("exeBinaryExtraTwo", buildTypeTwo, platformTwo, [sourceOne, extraSource])

        and:
        executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne]
        executable.getBinaries() >> new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [executableBinary, executableBinary2, executableBinaryExtra, executableBinaryExtra2])

        then:
        checkNames executableBinary, "platformOneExeNameExe", 'buildTypeOne', 'Win32'
        checkNames executableBinary2, "platformOneExeNameExe", 'buildTypeTwo', 'Win32'
        checkNames executableBinaryExtra, "platformTwoExeNameExe", 'buildTypeOne', 'Win32'
        checkNames executableBinaryExtra2, "platformTwoExeNameExe", 'buildTypeTwo', 'Win32'
    }

    def "maps same project name for native component that has multiple platforms with different architectures"() {
        when:
        def platformTwo = Mock(Platform)
        platformTwo.name >> "platformTwo"
        platformTwo.architecture >> arch("amd64")
        def platformTwoBinary = createExecutableBinary("platformTwoBinary", buildTypeOne, platformTwo, [sourceOne])

        and:
        executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne, platformTwo]
        executable.binaries >> new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [executableBinary, platformTwoBinary])

        then:
        platformOne.architecture != platformTwo.architecture
        checkNames executableBinary, "exeNameExe", "buildTypeOne", "Win32"
        checkNames platformTwoBinary, "exeNameExe", "buildTypeOne", "x64"
    }

    def "maps different configuration names for native component that has multiple platforms with same architecture"() {
        when:
        def platformTwo = Mock(Platform)
        platformTwo.architecture >> arch("i386")
        platformTwo.name >> "platformTwo"
        def platformTwoBinary = createExecutableBinary("platformTwoBinary", buildTypeOne, platformTwo, [sourceOne])

        and:
        executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne, platformTwo]
        executable.binaries >> new DefaultDomainObjectSet<NativeBinary>(NativeBinary, [executableBinary, platformTwoBinary])

        then:
        checkNames executableBinary, "exeNameExe", "platformOneBuildTypeOne", "Win32"
        checkNames platformTwoBinary, "exeNameExe", "platformTwoBuildTypeOne", "Win32"
    }

    private def createExecutableBinary(String binaryName, def buildType, def platform, def sources) {
        def binary = Mock(ExecutableBinaryInternal)
        binary.name >> binaryName
        binary.component >> executable
        binary.buildType >> buildType
        binary.flavor >> flavorOne
        binary.targetPlatform >> platform
        binary.source >> source(sources)
        return binary
    }

    private checkNames(def binary, def projectName, def configurationName, def platformName) {
        def names = mapper.mapToConfiguration(binary)
        assert names.project == projectName
        assert names.configuration == configurationName
        assert names.platform == platformName
        true
    }

    private static Architecture arch(String name) {
        return ArchitectureNotationParser.parser().parseNotation(name)
    }

    private DefaultDomainObjectSet<LanguageSourceSet> source(List sources) {
        new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet, sources)
    }

    private libraryBinary(Class<? extends LibraryBinary> type) {
        def binary = Mock(type)
        binary.component >> library
        binary.flavor >> flavorOne
        binary.targetPlatform >> platformOne
        binary.buildType >> buildTypeOne
        binary.source >> source([sourceOne])
        return binary
    }

    interface ExecutableInternal extends Executable, ProjectNativeComponentInternal {}
    interface LibraryInternal extends Library, ProjectNativeComponentInternal {}
    interface ExecutableBinaryInternal extends ExecutableBinary, ProjectNativeBinaryInternal {}
    interface SharedLibraryBinaryInternal extends SharedLibraryBinary, ProjectNativeBinaryInternal {}
    interface StaticLibraryBinaryInternal extends StaticLibraryBinary, ProjectNativeBinaryInternal {}
}
