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
    def library = Mock(LibraryInternal)

    def flavorOne = Mock(Flavor)
    def buildTypeOne = Mock(BuildType)
    def buildTypeTwo = Mock(BuildType)
    def platformOne = Mock(Platform)

    def setup() {
        executableBinary = createExecutableBinary("exeBinaryName", buildTypeOne, platformOne)

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

        then:
        checkNames executableBinary, "exeNameExe", 'buildTypeOne', 'Win32'
    }

    def "maps library binary types to visual studio projects"() {
        when:
        def sharedLibraryBinary = libraryBinary(SharedLibraryBinaryInternal)
        def staticLibraryBinary = libraryBinary(StaticLibraryBinaryInternal)

        library.chooseFlavors(flavors) >> [flavorOne]
        library.choosePlatforms(platforms) >> [platformOne]

        then:
        checkNames sharedLibraryBinary, "libNameDll", 'buildTypeOne', 'Win32'
        checkNames staticLibraryBinary, "libNameLib", 'buildTypeOne', 'Win32'
    }

    def "maps build type to configuration names for native binary"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne]

        then:
        checkNames executableBinary, "exeNameExe", "buildTypeOne", "Win32"
    }

    def "includes flavor name in configuration where component has multiple flavors"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne, new DefaultFlavor("flavorTwo")]
        executable.choosePlatforms(platforms) >> [platformOne]

        then:
        checkNames executableBinary, "exeNameExe", 'flavorOneBuildTypeOne', 'Win32'
    }

    def "includes platform name in configuration where component has multiple platforms"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne, Mock(Platform)]

        then:
        checkNames executableBinary, "exeNameExe", 'buildTypeOnePlatformOne', 'Win32'
    }

    def "includes flavor and platform name in configuration where component has multiple of both"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne, new DefaultFlavor("flavor2")]
        executable.choosePlatforms(platforms) >> [platformOne, Mock(Platform)]

        then:
        checkNames executableBinary, "exeNameExe", 'flavorOneBuildTypeOnePlatformOne', 'Win32'
    }

    private def createExecutableBinary(String binaryName, def buildType, def platform) {
        def binary = Mock(ExecutableBinaryInternal)
        binary.name >> binaryName
        binary.component >> executable
        binary.buildType >> buildType
        binary.flavor >> flavorOne
        binary.targetPlatform >> platform
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

    private libraryBinary(Class<? extends LibraryBinary> type) {
        def binary = Mock(type)
        binary.component >> library
        binary.flavor >> flavorOne
        binary.targetPlatform >> platformOne
        binary.buildType >> buildTypeOne
        return binary
    }

    interface ExecutableInternal extends Executable, ProjectNativeComponentInternal {}
    interface LibraryInternal extends Library, ProjectNativeComponentInternal {}
    interface ExecutableBinaryInternal extends ExecutableBinary, ProjectNativeBinaryInternal {}
    interface SharedLibraryBinaryInternal extends SharedLibraryBinary, ProjectNativeBinaryInternal {}
    interface StaticLibraryBinaryInternal extends StaticLibraryBinary, ProjectNativeBinaryInternal {}
}
