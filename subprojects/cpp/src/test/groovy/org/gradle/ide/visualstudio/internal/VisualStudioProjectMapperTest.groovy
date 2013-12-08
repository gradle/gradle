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
import org.gradle.nativebinaries.internal.ArchitectureNotationParser
import org.gradle.nativebinaries.internal.DefaultFlavor
import org.gradle.nativebinaries.internal.NativeComponentInternal
import spock.lang.Specification

class VisualStudioProjectMapperTest extends Specification {
    def flavors = Mock(FlavorContainer)
    def platforms = Mock(PlatformContainer)
    def mapper = new VisualStudioProjectMapper(flavors, platforms)

    def executable = Mock(ExecutableInternal)
    def executableBinary = Mock(ExecutableBinary)
    def library = Mock(LibraryInternal)

    def flavorOne = Mock(Flavor)
    def buildTypeOne = Mock(BuildType)
    def platformOne = Mock(Platform)


    def setup() {
        executableBinary.component >> executable

        executableBinary.buildType >> buildTypeOne
        executableBinary.flavor >> flavorOne
        executableBinary.targetPlatform >> platformOne

        executable.baseName >> "exeName"
        library.baseName >> "libName"

        flavorOne.name >> "flavorOne"
        buildTypeOne.name >> "buildTypeOne"
        platformOne.name >> "platformOne"
        platformOne.architecture >> arch("i386")
    }

    def "maps library binary types to visual studio projects"() {
        when:
        def sharedLibraryBinary = libraryBinary(SharedLibraryBinary)
        def staticLibraryBinary = libraryBinary(StaticLibraryBinary)

        library.chooseFlavors(flavors) >> [flavorOne]
        library.choosePlatforms(platforms) >> [platformOne]

        then:
        checkNames sharedLibraryBinary, "libNameDll", 'buildTypeOne', 'Win32'
        checkNames staticLibraryBinary, "libNameLib", 'buildTypeOne', 'Win32'
    }

    def "maps different project names for native component that have multiple flavors"() {
        when:
        1 * executable.chooseFlavors(flavors) >> [flavorOne]
        executable.choosePlatforms(platforms) >> [platformOne]

        then:
        checkNames executableBinary, "exeNameExe", 'buildTypeOne', 'Win32'

        when:
        1 * executable.chooseFlavors(flavors) >> [flavorOne, new DefaultFlavor("flavorTwo")]
        executable.choosePlatforms(platforms) >> [platformOne]

        then:
        checkNames executableBinary, "flavorOneExeNameExe", 'buildTypeOne', 'Win32'
    }

    def "maps same project name for native component that has multiple platforms with different architectures"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne]

        and:
        def platformTwo = Mock(Platform)
        platformTwo.architecture >> arch("amd64")

        executable.choosePlatforms(platforms) >> [platformOne, platformTwo]

        and:
        def executableBinary2 = Mock(ExecutableBinary)
        executableBinary2.component >> executable
        executableBinary2.buildType >> buildTypeOne
        executableBinary2.flavor >> flavorOne
        executableBinary2.targetPlatform >> platformTwo

        then:
        platformOne.architecture != platformTwo.architecture
        checkNames executableBinary, "exeNameExe", "buildTypeOne", "Win32"
        checkNames executableBinary2, "exeNameExe", "buildTypeOne", "x64"
    }

    def "maps different configuration names for native component that has multiple platforms with same architecture"() {
        when:
        executable.chooseFlavors(flavors) >> [flavorOne]

        and:
        def platformTwo = Mock(Platform)
        platformTwo.architecture >> arch("i386")
        platformTwo.name >> "platformTwo"

        executable.choosePlatforms(platforms) >> [platformOne, platformTwo]

        and:
        def executableBinary2 = Mock(ExecutableBinary)
        executableBinary2.component >> executable
        executableBinary2.buildType >> buildTypeOne
        executableBinary2.flavor >> flavorOne
        executableBinary2.targetPlatform >> platformTwo

        then:
        checkNames executableBinary, "exeNameExe", "platformOneBuildTypeOne", "Win32"
        checkNames executableBinary2, "exeNameExe", "platformTwoBuildTypeOne", "Win32"
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

    interface ExecutableInternal extends Executable, NativeComponentInternal {}
    interface LibraryInternal extends Library, NativeComponentInternal {}
}
