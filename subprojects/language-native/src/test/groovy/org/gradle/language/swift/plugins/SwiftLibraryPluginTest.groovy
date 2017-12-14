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
import org.gradle.language.swift.SwiftLibrary
import org.gradle.language.swift.SwiftSharedLibrary
import org.gradle.language.swift.SwiftStaticLibrary
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftLibraryPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testLib").build()

    def "adds extension with convention for source layout and module name"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.evaluate()

        then:
        project.library instanceof SwiftLibrary
        project.library.module.get() == "TestLib"
        project.library.swiftSource.files == [src] as Set
    }

    def "registers a component for the library with default linkage"() {
        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.evaluate()

        then:
        project.components.main == project.library
        project.library.binaries.get().name == ['mainDebug', 'mainRelease']
        project.components.containsAll(project.library.binaries.get())

        and:
        def binaries = project.library.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof SwiftSharedLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof SwiftSharedLibrary }.size() == 1

        and:
        project.library.developmentBinary.get() == binaries.find { it.debuggable && !it.optimized && it instanceof SwiftSharedLibrary }
    }

    def "registers a component for the library with static linkage"() {
        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.library.linkage = [Linkage.STATIC]
        project.evaluate()

        then:
        project.components.main == project.library
        project.library.binaries.get().name == ['mainDebugStatic', 'mainReleaseStatic']
        project.components.containsAll(project.library.binaries.get())

        and:
        def binaries = project.library.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof SwiftStaticLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof SwiftStaticLibrary }.size() == 1
    }

    def "registers a component for the library with both linkage"() {
        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.library.linkage = [Linkage.SHARED, Linkage.STATIC]
        project.evaluate()

        then:
        project.components.main == project.library
        project.library.binaries.get().name == ['mainDebug', 'mainRelease', 'mainDebugStatic', 'mainReleaseStatic']
        project.components.containsAll(project.library.binaries.get())

        and:
        def binaries = project.library.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof SwiftSharedLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof SwiftSharedLibrary }.size() == 1
        binaries.findAll { it.debuggable && it.optimized && it instanceof SwiftStaticLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof SwiftStaticLibrary }.size() == 1

        and:
        project.library.developmentBinary.get() == binaries.find { it.debuggable && !it.optimized && it instanceof SwiftSharedLibrary }
    }

    def "adds compile and link tasks for default linkage"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.evaluate()

        then:
        project.tasks.withType(SwiftCompile).name == ['compileDebugSwift', 'compileReleaseSwift']
        project.tasks.withType(CreateStaticLibrary).empty

        and:
        def compileDebug = project.tasks.compileDebugSwift
        compileDebug instanceof SwiftCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug")
        compileDebug.moduleFile.get().asFile == projectDir.file("build/modules/main/debug/TestLib.swiftmodule")
        compileDebug.debuggable
        !compileDebug.optimized

        def linkDebug = project.tasks.linkDebug
        linkDebug instanceof LinkSharedLibrary
        linkDebug.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("TestLib"))
        linkDebug.debuggable

        def compileRelease = project.tasks.compileReleaseSwift
        compileRelease instanceof SwiftCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDir.get().asFile == projectDir.file("build/obj/main/release")
        compileRelease.moduleFile.get().asFile == projectDir.file("build/modules/main/release/TestLib.swiftmodule")
        compileRelease.debuggable
        compileRelease.optimized

        def linkRelease = project.tasks.linkRelease
        linkRelease instanceof LinkSharedLibrary
        linkRelease.binaryFile.get().asFile == projectDir.file("build/lib/main/release/" + OperatingSystem.current().getSharedLibraryName("TestLib"))
        linkRelease.debuggable
    }

    def "adds compile and link tasks for both linkage"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.library.linkage = [Linkage.SHARED, Linkage.STATIC]
        project.evaluate()

        then:
        def compileDebug = project.tasks.compileDebugSwift
        compileDebug instanceof SwiftCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug")
        compileDebug.moduleFile.get().asFile == projectDir.file("build/modules/main/debug/TestLib.swiftmodule")
        compileDebug.debuggable
        !compileDebug.optimized

        def linkDebug = project.tasks.linkDebug
        linkDebug instanceof LinkSharedLibrary
        linkDebug.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("TestLib"))
        linkDebug.debuggable

        def compileRelease = project.tasks.compileReleaseSwift
        compileRelease instanceof SwiftCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDir.get().asFile == projectDir.file("build/obj/main/release")
        compileRelease.moduleFile.get().asFile == projectDir.file("build/modules/main/release/TestLib.swiftmodule")
        compileRelease.debuggable
        compileRelease.optimized

        def linkRelease = project.tasks.linkRelease
        linkRelease instanceof LinkSharedLibrary
        linkRelease.binaryFile.get().asFile == projectDir.file("build/lib/main/release/" + OperatingSystem.current().getSharedLibraryName("TestLib"))
        linkRelease.debuggable

        and:
        def compileDebugStatic = project.tasks.compileDebugStaticSwift
        compileDebugStatic instanceof SwiftCompile
        compileDebugStatic.source.files == [src] as Set
        compileDebugStatic.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug/static")
        compileDebugStatic.moduleFile.get().asFile == projectDir.file("build/modules/main/debug/static/TestLib.swiftmodule")
        compileDebugStatic.debuggable
        !compileDebugStatic.optimized

        def createDebugStatic = project.tasks.createDebugStatic
        createDebugStatic instanceof CreateStaticLibrary
        createDebugStatic.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/static/" + OperatingSystem.current().getStaticLibraryName("TestLib"))

        def compileReleaseStatic = project.tasks.compileReleaseStaticSwift
        compileReleaseStatic instanceof SwiftCompile
        compileReleaseStatic.source.files == [src] as Set
        compileReleaseStatic.objectFileDir.get().asFile == projectDir.file("build/obj/main/release/static")
        compileReleaseStatic.moduleFile.get().asFile == projectDir.file("build/modules/main/release/static/TestLib.swiftmodule")
        compileReleaseStatic.debuggable
        compileReleaseStatic.optimized

        def createReleaseStatic = project.tasks.createReleaseStatic
        createReleaseStatic instanceof CreateStaticLibrary
        createReleaseStatic.binaryFile.get().asFile == projectDir.file("build/lib/main/release/static/" + OperatingSystem.current().getStaticLibraryName("TestLib"))
    }

    def "adds compile and link tasks for static linkage only"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.library.linkage = [Linkage.STATIC]
        project.evaluate()

        then:
        project.tasks.withType(SwiftCompile).name == ['compileDebugStaticSwift', 'compileReleaseStaticSwift']
        project.tasks.withType(LinkSharedLibrary).empty

        and:
        def compileDebug = project.tasks.compileDebugStaticSwift
        compileDebug instanceof SwiftCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug/static")
        compileDebug.moduleFile.get().asFile == projectDir.file("build/modules/main/debug/static/TestLib.swiftmodule")
        compileDebug.debuggable
        !compileDebug.optimized

        def createDebug = project.tasks.createDebugStatic
        createDebug instanceof CreateStaticLibrary
        createDebug.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/static/" + OperatingSystem.current().getStaticLibraryName("TestLib"))

        def compileRelease = project.tasks.compileReleaseStaticSwift
        compileRelease instanceof SwiftCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDir.get().asFile == projectDir.file("build/obj/main/release/static")
        compileRelease.moduleFile.get().asFile == projectDir.file("build/modules/main/release/static/TestLib.swiftmodule")
        compileRelease.debuggable
        compileRelease.optimized

        def createRelease = project.tasks.createReleaseStatic
        createRelease instanceof CreateStaticLibrary
        createRelease.binaryFile.get().asFile == projectDir.file("build/lib/main/release/static/" + OperatingSystem.current().getStaticLibraryName("TestLib"))
    }

    def "output file names are calculated from module name defined on extension"() {
        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.library.module = "Lib"
        project.evaluate()

        then:
        def compileSwift = project.tasks.compileDebugSwift
        compileSwift.moduleName.get() == "Lib"
        compileSwift.moduleFile.get().asFile == projectDir.file("build/modules/main/debug/Lib.swiftmodule")

        def link = project.tasks.linkDebug
        link.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("Lib"))
    }
}
