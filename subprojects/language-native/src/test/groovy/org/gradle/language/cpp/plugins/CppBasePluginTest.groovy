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

package org.gradle.language.cpp.plugins

import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.provider.Property
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.CppPlatform
import org.gradle.language.cpp.internal.DefaultCppApplication
import org.gradle.language.cpp.internal.DefaultCppBinary
import org.gradle.language.cpp.internal.DefaultCppExecutable
import org.gradle.language.cpp.internal.DefaultCppSharedLibrary
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.nativeplatform.internal.Names
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
import org.junit.Rule
import spock.lang.Specification

class CppBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("test").build()

    def "adds compile task for binary"() {
        def binary = Stub(DefaultCppBinary)
        binary.name >> name
        binary.names >> Names.of(name)
        binary.targetPlatform >> Stub(CppPlatformInternal)

        when:
        project.pluginManager.apply(CppBasePlugin)
        project.components.add(binary)

        then:
        def compileCpp = project.tasks[taskName]
        compileCpp instanceof CppCompile
        compileCpp.objectFileDir.get().asFile == projectDir.file("build/obj/${objDir}")

        where:
        name        | taskName              | objDir
        "main"      | "compileCpp"          | "main"
        "mainDebug" | "compileDebugCpp"     | "main/debug"
        "test"      | "compileTestCpp"      | "test"
        "testDebug" | "compileTestDebugCpp" | "test/debug"
    }

    def "adds link and install task for executable"() {
        def baseName = project.objects.property(String)
        baseName.set("test_app")
        def executable = Stub(DefaultCppExecutable)
        def executableFile = project.layout.fileProperty()
        executable.name >> name
        executable.names >> Names.of(name)
        executable.baseName >> baseName
        executable.getExecutableFile() >> executableFile
        executable.targetPlatform >> Stub(CppPlatformInternal)
        executable.platformToolProvider >> new TestPlatformToolProvider()
        executable.implementationDependencies >> Stub(ConfigurationInternal)

        when:
        project.pluginManager.apply(CppBasePlugin)
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
        def baseName = project.objects.property(String)
        baseName.set("test_lib")
        def library = Stub(DefaultCppSharedLibrary)
        library.name >> name
        library.names >> Names.of(name)
        library.baseName >> baseName
        library.targetPlatform >> Stub(CppPlatformInternal)
        library.platformToolProvider >> new TestPlatformToolProvider()
        library.implementationDependencies >> Stub(ConfigurationInternal)

        when:
        project.pluginManager.apply(CppBasePlugin)
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
        def component = Stub(DefaultCppApplication)
        def prop = Stub(Property)
        prop.get() >> "SomeApp"
        component.baseName >> prop

        when:
        project.pluginManager.apply(CppBasePlugin)
        project.components.add(component)
        project.evaluate()

        then:
        def publications = project.services.get(ProjectPublicationRegistry).getPublications(project.path)
        publications.size() == 1
        publications.first().getCoordinates(SwiftPmTarget).targetName == "SomeApp"
    }

    interface CppPlatformInternal extends CppPlatform, NativePlatformInternal {}

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
