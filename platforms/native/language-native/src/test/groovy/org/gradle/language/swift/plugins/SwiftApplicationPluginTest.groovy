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
import org.gradle.language.swift.SwiftExecutable
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftApplicationPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout and module name"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftApplicationPlugin)
        project.evaluate()

        then:
        project.application instanceof SwiftApplication
        project.application.module.get() == "TestApp"
        project.application.swiftSource.files == [src] as Set
    }

    def "registers a component for the application"() {
        when:
        project.pluginManager.apply(SwiftApplicationPlugin)
        project.evaluate()

        then:
        project.components.main == project.application
        project.application.binaries.get().name == ['mainDebug', 'mainRelease']
        project.components.containsAll(project.application.binaries.get())

        and:
        def binaries = project.application.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof SwiftExecutable }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof SwiftExecutable }.size() == 1

        and:
        project.application.developmentBinary.get() == binaries.find { it.debuggable && !it.optimized && it instanceof SwiftExecutable }
    }

    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftApplicationPlugin)
        project.evaluate()

        then:
        project.tasks.withType(SwiftCompile)*.name == ['compileDebugSwift', 'compileReleaseSwift']

        and:
        def compileDebug = project.tasks.compileDebugSwift
        compileDebug instanceof SwiftCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug")
        compileDebug.moduleFile.get().asFile == projectDir.file("build/modules/main/debug/TestApp.swiftmodule")
        compileDebug.debuggable
        !compileDebug.optimized

        def linkDebug = project.tasks.linkDebug
        linkDebug instanceof LinkExecutable
        linkDebug.linkedFile.get().asFile == projectDir.file("build/exe/main/debug/" + OperatingSystem.current().getExecutableName("TestApp"))
        linkDebug.debuggable

        def installDebug = project.tasks.installDebug
        installDebug instanceof InstallExecutable
        installDebug.installDirectory.get().asFile == projectDir.file("build/install/main/debug")
        installDebug.runScriptFile.get().getAsFile().name == OperatingSystem.current().getScriptName("TestApp")

        def compileRelease = project.tasks.compileReleaseSwift
        compileRelease instanceof SwiftCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDir.get().asFile == projectDir.file("build/obj/main/release")
        compileRelease.moduleFile.get().asFile == projectDir.file("build/modules/main/release/TestApp.swiftmodule")
        compileRelease.debuggable
        compileRelease.optimized

        def linkRelease = project.tasks.linkRelease
        linkRelease instanceof LinkExecutable
        linkRelease.linkedFile.get().asFile == projectDir.file("build/exe/main/release/" + OperatingSystem.current().getExecutableName("TestApp"))
        linkRelease.debuggable

        def installRelease = project.tasks.installRelease
        installRelease instanceof InstallExecutable
        installRelease.installDirectory.get().asFile == projectDir.file("build/install/main/release")
        installRelease.runScriptFile.get().getAsFile().name == OperatingSystem.current().getScriptName("TestApp")
    }

    def "output file names are calculated from module name defined on extension"() {
        when:
        project.pluginManager.apply(SwiftApplicationPlugin)
        project.application.module = "App"
        project.evaluate()

        then:
        def compileSwift = project.tasks.compileDebugSwift
        compileSwift.moduleName.get() == "App"
        compileSwift.moduleFile.get().asFile == projectDir.file("build/modules/main/debug/App.swiftmodule")

        def link = project.tasks.linkDebug
        link.linkedFile.get().asFile == projectDir.file("build/exe/main/debug/" + OperatingSystem.current().getExecutableName("App"))

        def install = project.tasks.installDebug
        install.installDirectory.get().asFile == projectDir.file("build/install/main/debug")
        install.runScriptFile.get().getAsFile().name == OperatingSystem.current().getScriptName("App")
    }

    def "output locations reflects changes to buildDir"() {
        given:
        project.pluginManager.apply(SwiftApplicationPlugin)
        project.evaluate()

        when:
        project.buildDir = "output"

        then:
        def compileSwift = project.tasks.compileDebugSwift
        compileSwift.objectFileDir.get().asFile == project.file("output/obj/main/debug")
        compileSwift.moduleFile.get().asFile == projectDir.file("output/modules/main/debug/TestApp.swiftmodule")

        def link = project.tasks.linkDebug
        link.linkedFile.get().asFile == projectDir.file("output/exe/main/debug/" + OperatingSystem.current().getExecutableName("TestApp"))

        def install = project.tasks.installDebug
        install.installDirectory.get().asFile == project.file("output/install/main/debug")
        install.executableFile.get().asFile == link.linkedFile.get().asFile

        link.linkedFile.set(project.file("exe"))
        install.executableFile.get().asFile == link.linkedFile.get().asFile
    }
}
