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
import org.gradle.language.swift.tasks.SwiftCompile
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

        then:
        project.library instanceof SwiftLibrary
        project.library.module.get() == "TestLib"
        project.library.swiftSource.files == [src] as Set
    }

    def "registers a component for the library"() {
        when:
        project.pluginManager.apply(SwiftLibraryPlugin)

        then:
        project.components.main == project.library
        project.components.mainDebug == project.library.debugSharedLibrary
        project.components.mainRelease == project.library.releaseSharedLibrary
    }

    def "adds compile and link tasks"() {
        given:
        def src = projectDir.file("src/main/swift/main.swift").createFile()

        when:
        project.pluginManager.apply(SwiftLibraryPlugin)

        then:
        def compileDebug = project.tasks.compileDebugSwift
        compileDebug instanceof SwiftCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDirectory.get().asFile == projectDir.file("build/obj/main/debug")

        def linkDebug = project.tasks.linkDebug
        linkDebug instanceof LinkSharedLibrary
        linkDebug.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("TestLib"))

        def compileRelease = project.tasks.compileReleaseSwift
        compileRelease instanceof SwiftCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDirectory.get().asFile == projectDir.file("build/obj/main/release")

        def linkRelease = project.tasks.linkRelease
        linkRelease instanceof LinkSharedLibrary
        linkRelease.binaryFile.get().asFile == projectDir.file("build/lib/main/release/" + OperatingSystem.current().getSharedLibraryName("TestLib"))
    }

    def "output file names are calculated from module name defined on extension"() {
        when:
        project.pluginManager.apply(SwiftLibraryPlugin)
        project.library.module = "Lib"

        then:
        def compileSwift = project.tasks.compileDebugSwift
        compileSwift.moduleName == "Lib"

        def link = project.tasks.linkDebug
        link.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("Lib"))
    }
}
