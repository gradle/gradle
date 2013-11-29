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

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativebinaries.*
import org.gradle.nativebinaries.internal.*
import org.gradle.nativebinaries.internal.resolve.LibraryNativeDependencySet
import spock.lang.Specification

class VisualStudioProjectRegistryTest extends Specification {
    def allFlavors = new DefaultFlavorContainer(new DirectInstantiator())
    final registry = new VisualStudioProjectRegistry(allFlavors)
    def executable = Mock(ExecutableInternal)
    def executableBinary = Mock(ExecutableBinary)
    def library = Mock(LibraryInternal)
    def sharedLibraryBinary = Mock(SharedLibraryBinary)
    def staticLibraryBinary = Mock(StaticLibraryBinary)

    def setup() {
        allFlavors.add(new DefaultFlavor(DefaultFlavor.DEFAULT))
        executable.chooseFlavors(allFlavors) >> allFlavors
        library.chooseFlavors(allFlavors) >> allFlavors
        executableBinary.libs >> []
        sharedLibraryBinary.libs >> []
        staticLibraryBinary.libs >> []
    }

    def "creates a matching visual studio project configuration for NativeBinary"() {
        when:
        executableBinary.component >> executable
        executable.baseName >> "myTest"

        and:
        def platform = Mock(Platform)

        executableBinary.buildType >> new DefaultBuildType(buildType)
        executableBinary.targetPlatform >> platform
        platform.architecture >> architecture

        then:
        def vsConfig = registry.getProjectConfiguration(executableBinary)
        vsConfig.configurationName == vsConfiguration
        vsConfig.platformName == vsPlatform

        where:
        buildType | architecture   | vsConfiguration | vsPlatform
        "debug"   | arch("i386")   | "debug"         | "Win32"
        "debug"   | arch("x86-64") | "debug"         | "x64"
        "debug"   | arch("ia-64")  | "debug"         | "Itanium"
    }

    def "creates visual studio project and project configuration for ExecutableBinary"() {
        when:
        executableBinary.component >> executable
        executable.baseName >> "myTest"

        then:
        def vsConfig = registry.getProjectConfiguration(executableBinary)
        vsConfig.type == "Application"

        def vsProject = vsConfig.project
        vsProject.name == "myTestExe"
    }

    def "creates visual studio project and project configuration for SharedLibraryBinary"() {
        when:
        sharedLibraryBinary.component >> library
        library.baseName >> "myTest"

        then:
        def vsConfig = registry.getProjectConfiguration(sharedLibraryBinary)
        vsConfig.type == "DynamicLibrary"

        def vsProject = vsConfig.project
        vsProject.name == "myTestDll"
    }

    def "creates visual studio project and project configuration for StaticLibraryBinary"() {
        when:
        staticLibraryBinary.component >> library
        library.baseName >> "myTest"

        then:
        def vsConfig = registry.getProjectConfiguration(staticLibraryBinary)
        vsConfig.type == "StaticLibrary"

        def vsProject = vsConfig.project
        vsProject.name == "myTestLib"
    }


    def "returns same visual studio project configuration for same native binary"() {
        when:
        sharedLibraryBinary.component >> library
        library.baseName >> "myTest"
        def vsConfig = registry.getProjectConfiguration(sharedLibraryBinary)

        then:
        registry.getProjectConfiguration(sharedLibraryBinary) == vsConfig
    }

    def "uses same visual studio project for native binaries that differ only in build type or architecture"() {
        when:
        def sharedLibraryBinary1 = Mock(SharedLibraryBinary)
        def platform1 = Mock(Platform)
        sharedLibraryBinary1.component >> library
        sharedLibraryBinary1.buildType >> new DefaultBuildType(buildType1)
        sharedLibraryBinary1.libs >> []
        sharedLibraryBinary1.targetPlatform >> platform1
        platform1.architecture >> arch1

        def sharedLibraryBinary2 = Mock(SharedLibraryBinary)
        def platform2 = Mock(Platform)
        sharedLibraryBinary2.component >> library
        sharedLibraryBinary2.buildType >> new DefaultBuildType(buildType2)
        sharedLibraryBinary2.libs >> []
        sharedLibraryBinary2.targetPlatform >> platform2
        platform2.architecture >> arch2

        then:
        def vsConfig1 = registry.getProjectConfiguration(sharedLibraryBinary1)
        def vsConfig2 = registry.getProjectConfiguration(sharedLibraryBinary2)

        vsConfig1.configurationName == buildType1
        vsConfig2.configurationName == buildType2
        vsConfig1.platformName != vsConfig2.platformName

        and:
        vsConfig1.project == vsConfig2.project

        where:
        buildType1 | buildType2 | arch1        | arch2
        "debug"    | "debug"    | arch("i386") | arch("x86-64")
        "debug"    | "release"  | arch("i386") | arch("ia-64")
    }

    def "adds project reference for each lib of native binary"() {
        when:
        def binary = Mock(ExecutableBinary)
        def dep1 = Mock(LibraryNativeDependencySet)
        def dep2 = Mock(LibraryNativeDependencySet)

        executable.baseName >> "myTest"
        library.baseName >> "myLibrary"

        binary.component >> executable
        binary.libs >> [dep1, dep2]

        dep1.libraryBinary >> sharedLibraryBinary
        sharedLibraryBinary.component >> library

        dep2.libraryBinary >> staticLibraryBinary
        staticLibraryBinary.component >> library

        then:
        def vsProject = registry.getProjectConfiguration(binary).project
        vsProject.projectReferences == ["myLibraryLib", "myLibraryDll"] as Set
    }

    private static Architecture arch(String name) {
        return ArchitectureNotationParser.parser().parseNotation(name)
    }

    interface ExecutableInternal extends Executable, NativeComponentInternal {}
    interface LibraryInternal extends Library, NativeComponentInternal {}
}
