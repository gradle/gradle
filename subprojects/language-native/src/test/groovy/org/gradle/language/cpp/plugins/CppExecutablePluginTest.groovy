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

import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class CppExecutablePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout and base name"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppExecutablePlugin)

        then:
        project.executable instanceof CppApplication
        project.executable.baseName.get() == "testApp"
        project.executable.cppSource.files == [src] as Set
    }

    def "registers a component for the executable"() {
        when:
        project.pluginManager.apply(CppExecutablePlugin)

        then:
        project.components.main == project.executable
        project.components.mainDebugShared == project.executable.debugExecutable
        project.components.mainReleaseShared == project.executable.releaseExecutable
        project.components.mainDebugStatic == project.executable.debugStaticExecutable
        project.components.mainReleaseStatic == project.executable.releaseStaticExecutable
    }

    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppExecutablePlugin)

        then:
        def compileDebugCpp = project.tasks.compileDebugSharedCpp
        compileDebugCpp instanceof CppCompile
        compileDebugCpp.includes.files == [project.file("src/main/headers")] as Set
        compileDebugCpp.source.files == [src] as Set
        compileDebugCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug/shared")
        compileDebugCpp.debuggable
        !compileDebugCpp.optimized

        def linkDebug = project.tasks.linkDebugShared
        linkDebug instanceof LinkExecutable
        linkDebug.binaryFile.get().asFile == projectDir.file("build/exe/main/debug/shared/" + OperatingSystem.current().getExecutableName("testApp"))
        linkDebug.debuggable

        def installDebug = project.tasks.installDebugShared
        installDebug instanceof InstallExecutable
        installDebug.installDirectory.get().asFile == projectDir.file("build/install/main/debug/shared")
        installDebug.runScript.name == OperatingSystem.current().getScriptName("testApp")

        def compileReleaseCpp = project.tasks.compileReleaseSharedCpp
        compileReleaseCpp instanceof CppCompile
        compileReleaseCpp.includes.files == [project.file("src/main/headers")] as Set
        compileReleaseCpp.source.files == [src] as Set
        compileReleaseCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/release/shared")
        !compileReleaseCpp.debuggable
        compileReleaseCpp.optimized

        def linkRelease = project.tasks.linkReleaseShared
        linkRelease instanceof LinkExecutable
        linkRelease.binaryFile.get().asFile == projectDir.file("build/exe/main/release/shared/" + OperatingSystem.current().getExecutableName("testApp"))
        !linkRelease.debuggable

        def installRelease = project.tasks.installReleaseShared
        installRelease instanceof InstallExecutable
        installRelease.installDirectory.get().asFile == projectDir.file("build/install/main/release/shared")
        installRelease.runScript.name == OperatingSystem.current().getScriptName("testApp")
    }

    def "output locations are calculated using base name defined on extension"() {
        when:
        project.pluginManager.apply(CppExecutablePlugin)
        project.executable.baseName = "test_app"

        then:
        def link = project.tasks.linkDebugShared
        link.binaryFile.get().asFile == projectDir.file("build/exe/main/debug/shared/" + OperatingSystem.current().getExecutableName("test_app"))

        def install = project.tasks.installDebugShared
        install.installDirectory.get().asFile == projectDir.file("build/install/main/debug/shared")
        install.runScript.name == OperatingSystem.current().getScriptName("test_app")
    }

    def "output locations reflects changes to buildDir"() {
        given:
        project.pluginManager.apply(CppExecutablePlugin)

        when:
        project.buildDir = "output"

        then:
        def compileCpp = project.tasks.compileDebugSharedCpp
        compileCpp.objectFileDir.get().asFile == project.file("output/obj/main/debug/shared")

        def link = project.tasks.linkDebugShared
        link.outputFile == projectDir.file("output/exe/main/debug/shared/" + OperatingSystem.current().getExecutableName("testApp"))

        def install = project.tasks.installDebugShared
        install.destinationDir == project.file("output/install/main/debug/shared")
        install.executable == link.outputFile

        link.setOutputFile(project.file("exe"))
        install.executable == link.outputFile
    }

    def "adds publications when maven-publish plugin is applied"() {
        when:
        project.pluginManager.apply(CppExecutablePlugin)
        project.pluginManager.apply(MavenPublishPlugin)
        project.version = 1.2
        project.group = 'my.group'
        project.executable.baseName = 'test_app'

        then:
        def publishing = project.publishing
        publishing.publications.size() == 5

        def main = publishing.publications.main
        main.groupId == 'my.group'
        main.artifactId == 'test_app'
        main.version == '1.2'
        main.artifacts.empty

        def debugShared = publishing.publications.debugShared
        debugShared.groupId == 'my.group'
        debugShared.artifactId == 'test_app_debugShared'
        debugShared.version == '1.2'
        debugShared.artifacts.size() == 1

        def releaseShared = publishing.publications.releaseShared
        releaseShared.groupId == 'my.group'
        releaseShared.artifactId == 'test_app_releaseShared'
        releaseShared.version == '1.2'
        releaseShared.artifacts.size() == 1

        def debugStatic = publishing.publications.debugStatic
        debugStatic.groupId == 'my.group'
        debugStatic.artifactId == 'test_app_debugStatic'
        debugStatic.version == '1.2'
        debugStatic.artifacts.size() == 1

        def releaseStatic = publishing.publications.releaseStatic
        releaseStatic.groupId == 'my.group'
        releaseStatic.artifactId == 'test_app_releaseStatic'
        releaseStatic.version == '1.2'
        releaseStatic.artifacts.size() == 1
    }
}
