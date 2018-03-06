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

package org.gradle.language.swift.plugins

import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.provider.LockableProperty
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Property
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.internal.DefaultSwiftApplication
import org.gradle.language.swift.internal.DefaultSwiftBinary
import org.gradle.language.swift.internal.DefaultSwiftExecutable
import org.gradle.language.swift.internal.DefaultSwiftSharedLibrary
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import org.gradle.swiftpm.internal.SwiftPmTarget
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.VersionNumber
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.language.swift.SwiftVersion.SWIFT3
import static org.gradle.language.swift.SwiftVersion.SWIFT4

class SwiftBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("test").build()

    def "adds compile task for binary"() {
        def binary = Stub(DefaultSwiftBinary)
        binary.name >> name
        binary.names >> Names.of(name)
        binary.module >> project.objects.property(String)
        binary.targetPlatform >> Stub(SwiftPlatformInternal)
        binary.sourceCompatibility >> Stub(LockableProperty) { getType() >> null }

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(binary)

        then:
        def compileSwift = project.tasks[taskName]
        compileSwift instanceof SwiftCompile
        compileSwift.objectFileDir.get().asFile == projectDir.file("build/obj/${objDir}")

        where:
        name        | taskName                | objDir
        "main"      | "compileSwift"          | "main"
        "mainDebug" | "compileDebugSwift"     | "main/debug"
        "test"      | "compileTestSwift"      | "test"
        "testDebug" | "compileTestDebugSwift" | "test/debug"
    }

    def "adds link and install task for executable"() {
        def executable = Stub(DefaultSwiftExecutable)
        def executableFile = project.layout.fileProperty()
        executable.name >> name
        executable.names >> Names.of(name)
        executable.module >> Providers.of("TestApp")
        executable.baseName >> Providers.of("test_app")
        executable.executableFile >> executableFile
        executable.targetPlatform >> Stub(SwiftPlatformInternal)
        executable.sourceCompatibility >> Stub(LockableProperty) { getType() >> null }
        executable.platformToolProvider >> new TestPlatformToolProvider()

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(executable)

        then:
        def link = project.tasks[linkTask]
        link instanceof LinkExecutable
        link.linkedFile.get().asFile == projectDir.file("build/exe/$exeDir" + OperatingSystem.current().getExecutableName("test_app"))

        def install = project.tasks[installTask]
        install instanceof InstallExecutable
        install.installDirectory.get().asFile == projectDir.file("build/install/$exeDir")

        where:
        name        | linkTask        | installTask        | exeDir
        "main"      | "link"          | "install"          | "main/"
        "mainDebug" | "linkDebug"     | "installDebug"     | "main/debug/"
        "test"      | "linkTest"      | "installTest"      | "test/"
        "testDebug" | "linkTestDebug" | "installTestDebug" | "test/debug/"
    }

    def "adds link task for shared library"() {
        def library = Stub(DefaultSwiftSharedLibrary)
        library.name >> name
        library.names >> Names.of(name)
        library.module >> Providers.of("TestLib")
        library.baseName >> Providers.of("test_lib")
        library.targetPlatform >> Stub(SwiftPlatformInternal)
        library.sourceCompatibility >> Stub(LockableProperty) { getType() >> null }
        library.platformToolProvider >> new TestPlatformToolProvider()
        library.implementationDependencies >> Stub(ConfigurationInternal)

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(library)

        then:
        def link = project.tasks[taskName]
        link instanceof LinkSharedLibrary
        link.linkedFile.get().asFile == projectDir.file("build/lib/${libDir}" + OperatingSystem.current().getSharedLibraryName("test_lib"))

        where:
        name        | taskName        | libDir
        "main"      | "link"          | "main/"
        "mainDebug" | "linkDebug"     | "main/debug/"
        "test"      | "linkTest"      | "test/"
        "testDebug" | "linkTestDebug" | "test/debug/"
    }

    @Unroll
    def "can associate the compiler version #compilerVersion to #languageVersion language version"() {
        expect:
        SwiftBasePlugin.toSwiftVersion(VersionNumber.parse(compilerVersion)) == languageVersion

        where:
        // See https://swift.org/download
        compilerVersion | languageVersion
        '4.0.3'         | SWIFT4
        '4.0.2'         | SWIFT4
        '4.0'           | SWIFT4
        '3.1.1'         | SWIFT3
        '3.1'           | SWIFT3
        '3.0.2'         | SWIFT3
        '3.0.1'         | SWIFT3
        '3.0'           | SWIFT3
    }

    def "throws exception when Swift language is unknown for specified compiler version"() {
        when:
        SwiftBasePlugin.toSwiftVersion(VersionNumber.parse("99.0.1"))

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == 'Swift language version is unknown for the specified Swift compiler version (99.0.1)'
    }

    def "registers a Swift PM publication for each production component"() {
        def component = Stub(DefaultSwiftApplication)
        def prop = Stub(Property)
        prop.get() >> "SomeApp"
        component.module >> prop

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(component)
        project.evaluate()

        then:
        def publications = project.services.get(ProjectPublicationRegistry).getPublications(project.path)
        publications.size() == 1
        publications.first().getCoordinates(SwiftPmTarget).targetName == "SomeApp"
    }

    interface SwiftPlatformInternal extends SwiftPlatform, NativePlatformInternal {}

    class TestPlatformToolProvider extends AbstractPlatformToolProvider {
        TestPlatformToolProvider() {
            super(null, new DefaultOperatingSystem("current", OperatingSystem.current()))
        }

        @Override
        SystemLibraries getSystemLibraries(ToolType compilerType) {
            throw new UnsupportedOperationException()
        }

        @Override
        ToolSearchResult isToolAvailable(ToolType toolType) {
            throw new UnsupportedOperationException()
        }
    }
}
