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
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class CppApplicationPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout and base name"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppApplicationPlugin)

        then:
        project.application instanceof CppApplication
        project.application.baseName.get() == "testApp"
        project.application.cppSource.files == [src] as Set
    }

    def "registers a component for the application"() {
        when:
        project.pluginManager.apply(CppApplicationPlugin)
        project.evaluate()

        then:
        project.components.main == project.application
        project.application.binaries.get().name == ['mainDebug', 'mainRelease']
        project.components.containsAll project.application.binaries.get()

        and:
        def binaries = project.application.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof CppExecutable }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof CppExecutable }.size() == 1

        and:
        project.application.developmentBinary.get() == binaries.find { it.debuggable && !it.optimized && it instanceof CppExecutable }
    }

    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppApplicationPlugin)
        project.evaluate()

        then:
        def compileDebugCpp = project.tasks.compileDebugCpp
        compileDebugCpp instanceof CppCompile
        compileDebugCpp.includes.files.first() == project.file("src/main/headers")
        compileDebugCpp.source.files == [src] as Set
        compileDebugCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug")
        compileDebugCpp.debuggable
        !compileDebugCpp.optimized

        def linkDebug = project.tasks.linkDebug
        linkDebug instanceof LinkExecutable
        linkDebug.linkedFile.get().asFile == projectDir.file("build/exe/main/debug/" + OperatingSystem.current().getExecutableName("testApp"))
        linkDebug.debuggable

        def installDebug = project.tasks.installDebug
        installDebug instanceof InstallExecutable
        installDebug.installDirectory.get().asFile == projectDir.file("build/install/main/debug")
        installDebug.runScriptFile.get().getAsFile().name == OperatingSystem.current().getScriptName("testApp")

        def compileReleaseCpp = project.tasks.compileReleaseCpp
        compileReleaseCpp instanceof CppCompile
        compileReleaseCpp.includes.files.first() == project.file("src/main/headers")
        compileReleaseCpp.source.files == [src] as Set
        compileReleaseCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/release")
        compileReleaseCpp.debuggable
        compileReleaseCpp.optimized

        def linkRelease = project.tasks.linkRelease
        linkRelease instanceof LinkExecutable
        linkRelease.linkedFile.get().asFile == projectDir.file("build/exe/main/release/" + OperatingSystem.current().getExecutableName("testApp"))
        linkRelease.debuggable

        def installRelease = project.tasks.installRelease
        installRelease instanceof InstallExecutable
        installRelease.installDirectory.get().asFile == projectDir.file("build/install/main/release")
        installRelease.runScriptFile.get().getAsFile().name == OperatingSystem.current().getScriptName("testApp")
    }

    def "output locations are calculated using base name defined on extension"() {
        when:
        project.pluginManager.apply(CppApplicationPlugin)
        project.evaluate()
        project.application.baseName = "test_app"

        then:
        def link = project.tasks.linkDebug
        link.linkedFile.get().asFile == projectDir.file("build/exe/main/debug/" + OperatingSystem.current().getExecutableName("test_app"))

        def install = project.tasks.installDebug
        install.installDirectory.get().asFile == projectDir.file("build/install/main/debug")
        install.runScriptFile.get().getAsFile().name == OperatingSystem.current().getScriptName("test_app")
    }

    def "output locations reflects changes to buildDir"() {
        given:
        project.pluginManager.apply(CppApplicationPlugin)
        project.evaluate()

        when:
        project.buildDir = "output"

        then:
        def compileCpp = project.tasks.compileDebugCpp
        compileCpp.objectFileDir.get().asFile == project.file("output/obj/main/debug")

        def link = project.tasks.linkDebug
        link.linkedFile.get().asFile == projectDir.file("output/exe/main/debug/" + OperatingSystem.current().getExecutableName("testApp"))

        def install = project.tasks.installDebug
        install.installDirectory.get().asFile == project.file("output/install/main/debug")
        install.executableFile.get().asFile == link.linkedFile.get().asFile

        link.linkedFile.set(project.file("exe"))
        install.executableFile.get().asFile == project.file("exe")
    }

    def "adds publications when maven-publish plugin is applied"() {
        when:
        project.pluginManager.apply(CppApplicationPlugin)
        project.pluginManager.apply(MavenPublishPlugin)
        project.version = 1.2
        project.group = 'my.group'
        project.application.baseName = 'test_app'
        project.evaluate()

        then:
        def publishing = project.publishing
        publishing.publications.size() == 3

        def main = publishing.publications.main
        main.groupId == 'my.group'
        main.artifactId == 'test_app'
        main.version == '1.2'
        main.artifacts.empty

        def debug = publishing.publications.mainDebug
        debug.groupId == 'my.group'
        debug.artifactId == 'test_app_debug'
        debug.version == '1.2'
        debug.artifacts.size() == 1

        def release = publishing.publications.mainRelease
        release.groupId == 'my.group'
        release.artifactId == 'test_app_release'
        release.version == '1.2'
        release.artifacts.size() == 1
    }
}
