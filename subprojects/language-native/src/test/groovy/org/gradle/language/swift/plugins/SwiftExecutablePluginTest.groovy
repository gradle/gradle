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

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.swift.SwiftApplication
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftExecutablePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout and module name"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftExecutablePlugin)

        then:
        project.executable instanceof SwiftApplication
        project.executable.module.get() == "TestApp"
        project.executable.swiftSource.files == [src] as Set
    }

    def "registers a component for the executable"() {
        when:
        project.pluginManager.apply(SwiftExecutablePlugin)

        then:
        project.components.main == project.executable
        project.components.mainDebug == project.executable.debugExecutable
        project.components.mainRelease == project.executable.releaseExecutable
    }

    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftExecutablePlugin)

        then:
        def compileDebug = project.tasks.compileDebugSwift
        compileDebug instanceof SwiftCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug")
        compileDebug.debuggable
        !compileDebug.optimized

        def linkDebug = project.tasks.linkDebug
        linkDebug instanceof LinkExecutable
        linkDebug.binaryFile.get().asFile == projectDir.file("build/exe/main/debug/" + OperatingSystem.current().getExecutableName("TestApp"))
        linkDebug.debuggable

        def installDebug = project.tasks.installDebug
        installDebug instanceof InstallExecutable
        installDebug.installDirectory.get().asFile == projectDir.file("build/install/main/debug")
        installDebug.runScript.name == OperatingSystem.current().getScriptName("TestApp")

        def compileRelease = project.tasks.compileReleaseSwift
        compileRelease instanceof SwiftCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDir.get().asFile == projectDir.file("build/obj/main/release")
        !compileRelease.debuggable
        compileRelease.optimized

        def linkRelease = project.tasks.linkRelease
        linkRelease instanceof LinkExecutable
        linkRelease.binaryFile.get().asFile == projectDir.file("build/exe/main/release/" + OperatingSystem.current().getExecutableName("TestApp"))
        !linkRelease.debuggable

        def installRelease = project.tasks.installRelease
        installRelease instanceof InstallExecutable
        installRelease.installDirectory.get().asFile == projectDir.file("build/install/main/release")
        installRelease.runScript.name == OperatingSystem.current().getScriptName("TestApp")
    }

    def "output file names are calculated from module name defined on extension"() {
        when:
        project.pluginManager.apply(SwiftExecutablePlugin)
        project.executable.module = "App"

        then:
        def compileSwift = project.tasks.compileDebugSwift
        compileSwift.moduleName == "App"

        def link = project.tasks.linkDebug
        link.binaryFile.get().asFile == projectDir.file("build/exe/main/debug/" + OperatingSystem.current().getExecutableName("App"))

        def install = project.tasks.installDebug
        install.installDirectory.get().asFile == projectDir.file("build/install/main/debug")
        install.runScript.name == OperatingSystem.current().getScriptName("App")
    }
}
