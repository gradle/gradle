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
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeExecutableSpec
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.NativeExecutableBinarySpecInternal
import org.gradle.nativeplatform.internal.SharedLibraryBinarySpecInternal
import org.gradle.nativeplatform.internal.StaticLibraryBinarySpecInternal
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.nativeplatform.test.NativeTestSuiteSpec
import org.gradle.nativeplatform.test.internal.NativeTestSuiteBinarySpecInternal
import org.gradle.platform.base.internal.BinaryNamingScheme
import spock.lang.Specification

class VisualStudioProjectMapperTest extends Specification {
    def mapper = new VisualStudioProjectMapper()

    def executable = Mock(NativeExecutableSpec)
    def library = Mock(NativeLibrarySpec)
    def namingScheme = Mock(BinaryNamingScheme)
    NativeExecutableBinarySpecInternal executableBinary

    def flavorOne = Mock(Flavor)
    def buildTypeOne = Mock(BuildType)
    def buildTypeTwo = Mock(BuildType)
    def platformOne = Mock(NativePlatform)

    def setup() {
        executableBinary = createExecutableBinary("exeBinaryName", buildTypeOne, platformOne)
        executableBinary.namingScheme >> namingScheme

        executable.name >> "exeName"
        library.name >> "libName"

        flavorOne.name >> "flavorOne"
        buildTypeOne.name >> "buildTypeOne"
        buildTypeTwo.name >> "buildTypeTwo"
        platformOne.name >> "platformOne"
        platformOne.architecture >> Architectures.forInput("i386")
    }

    def "maps executable binary to visual studio project"() {
        when:
        executable.projectPath >> ":"
        namingScheme.variantDimensions >> []

        then:
        checkNames executableBinary, "exeNameExe", 'buildTypeOne', 'Win32'
    }

    def "maps library binary types to visual studio projects"() {
        when:
        def sharedLibraryBinary = libraryBinary(SharedLibraryBinarySpecInternal)
        def staticLibraryBinary = libraryBinary(StaticLibraryBinarySpecInternal)

        library.projectPath >> ":"
        namingScheme.variantDimensions >> []

        then:
        checkNames sharedLibraryBinary, "libNameDll", 'buildTypeOne', 'Win32'
        checkNames staticLibraryBinary, "libNameLib", 'buildTypeOne', 'Win32'
    }

    def "maps test binary to visual studio project"() {
        def testExecutable = Mock(NativeTestSuiteSpec)
        def binary = Mock(NativeTestSuiteBinarySpecInternal)

        when:
        testExecutable.name >> "testSuiteName"
        testExecutable.projectPath >> ":"
        binary.component >> testExecutable
        binary.buildType >> buildTypeOne
        binary.flavor >> flavorOne
        binary.targetPlatform >> platformOne
        binary.namingScheme >> namingScheme
        namingScheme.variantDimensions >> []

        then:
        checkNames binary, "testSuiteNameExe", 'buildTypeOne', 'Win32'
    }

    def "includes project path in visual studio project name"() {
        when:
        executable.projectPath >> ":subproject:name"

        and:
        namingScheme.variantDimensions >> []

        then:
        checkNames executableBinary, "subproject_name_exeNameExe", 'buildTypeOne', 'Win32'
    }

    def "uses single variant dimension for configuration name where not empty"() {
        when:
        executable.projectPath >> ":"
        namingScheme.variantDimensions >> ["flavorOne"]

        then:
        checkNames executableBinary, "exeNameExe", 'flavorOne', 'Win32'
    }

    def "includes variant dimensions in configuration where component has multiple dimensions"() {
        when:
        executable.projectPath >> ":"
        namingScheme.variantDimensions >> ["platformOne", "buildTypeOne", "flavorOne"]

        then:
        checkNames executableBinary, "exeNameExe", 'platformOneBuildTypeOneFlavorOne', 'Win32'
    }

    private def createExecutableBinary(String binaryName, def buildType, def platform) {
        def binary = Mock(NativeExecutableBinarySpecInternal)
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

    private libraryBinary(Class<? extends NativeBinarySpecInternal> type) {
        def binary = Mock(type)
        binary.component >> library
        binary.flavor >> flavorOne
        binary.targetPlatform >> platformOne
        binary.buildType >> buildTypeOne
        binary.namingScheme >> namingScheme
        return binary
    }
}
