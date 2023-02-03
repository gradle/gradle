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
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Property
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.nativeplatform.internal.Names
import org.gradle.language.swift.SwiftPlatform
import org.gradle.language.swift.SwiftVersion
import org.gradle.language.swift.internal.DefaultSwiftApplication
import org.gradle.language.swift.internal.DefaultSwiftBinary
import org.gradle.language.swift.internal.DefaultSwiftExecutable
import org.gradle.language.swift.internal.DefaultSwiftSharedLibrary
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.platform.internal.DefaultOperatingSystem
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult
import org.gradle.swiftpm.internal.NativeProjectPublication
import org.gradle.swiftpm.internal.SwiftPmTarget
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("test").build()

    def "adds compile task for binary"() {
        def binary = Stub(DefaultSwiftBinary)
        binary.name >> name
        binary.names >> Names.of(name)
        binary.module >> project.objects.property(String)
        binary.targetMachine >> Stub(TargetMachine)
        binary.targetPlatform >> Stub(SwiftPlatform)
        binary.sourceCompatibility >> project.objects.property(SwiftVersion)

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
        def executableFile = project.objects.fileProperty()
        executable.name >> name
        executable.names >> Names.of(name)
        executable.module >> Providers.of("TestApp")
        executable.baseName >> Providers.of("test_app")
        executable.executableFile >> executableFile
        executable.targetMachine >> Stub(SwiftPlatform)
        executable.sourceCompatibility >> project.objects.property(SwiftVersion)
        executable.platformToolProvider >> new TestPlatformToolProvider()
        executable.implementationDependencies >> Stub(ConfigurationInternal)

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
        library.targetMachine >> Stub(SwiftPlatform)
        library.sourceCompatibility >> Stub(PropertyInternal) { getType() >> null }
        library.platformToolProvider >> new TestPlatformToolProvider()
        library.linkFile >> project.objects.fileProperty()
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
        def publications = project.services.get(ProjectPublicationRegistry).getPublications(NativeProjectPublication, project.identityPath)
        publications.size() == 1
        publications.first().getCoordinates(SwiftPmTarget).targetName == "SomeApp"
    }

    class TestPlatformToolProvider extends AbstractPlatformToolProvider {
        TestPlatformToolProvider() {
            super(null, new DefaultOperatingSystem("current", OperatingSystem.current()))
        }

        @Override
        SystemLibraries getSystemLibraries(ToolType compilerType) {
            throw new UnsupportedOperationException()
        }

        @Override
        CommandLineToolSearchResult locateTool(ToolType compilerType) {
            throw new UnsupportedOperationException()
        }
    }
}
