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
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class CppLibraryPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testLib").build()

    def "adds extension with convention for source layout and base name"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()
        def publicHeaders = projectDir.file("src/main/public").createDir()

        when:
        project.pluginManager.apply(CppLibraryPlugin)

        then:
        project.library instanceof CppLibrary
        project.library.baseName.get() == "testLib"
        project.library.cppSource.files == [src] as Set
        project.library.publicHeaderDirs.files == [publicHeaders] as Set
    }

    def "registers a component for the library"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)

        then:
        project.components.main == project.library
        project.components.mainDebugShared == project.library.debugSharedLibrary
        project.components.mainReleaseShared == project.library.releaseSharedLibrary
        project.components.mainDebugStatic == project.library.debugStaticLibrary
        project.components.mainReleaseStatic == project.library.releaseStaticLibrary
    }

    def "adds compile and link tasks"() {
        given:
        def src = projectDir.file("src/main/cpp/lib.cpp").createFile()
        def publicHeaders = projectDir.file("src/main/public").createDir()
        def privateHeaders = projectDir.file("src/main/headers").createDir()

        when:
        project.pluginManager.apply(CppLibraryPlugin)

        then:
        def compileDebugCpp = project.tasks.compileDebugSharedCpp
        compileDebugCpp instanceof CppCompile
        compileDebugCpp.includes.files as List == [publicHeaders, privateHeaders]
        compileDebugCpp.source.files as List == [src]
        compileDebugCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug/shared")
        compileDebugCpp.debuggable
        !compileDebugCpp.optimized

        def linkDebug = project.tasks.linkDebugShared
        linkDebug instanceof LinkSharedLibrary
        linkDebug.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/shared/" + OperatingSystem.current().getSharedLibraryName("testLib"))
        linkDebug.debuggable

        def compileReleaseCpp = project.tasks.compileReleaseSharedCpp
        compileReleaseCpp instanceof CppCompile
        compileReleaseCpp.includes.files as List == [publicHeaders, privateHeaders]
        compileReleaseCpp.source.files as List == [src]
        compileReleaseCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/release/shared")
        !compileReleaseCpp.debuggable
        compileReleaseCpp.optimized

        def linkRelease = project.tasks.linkReleaseShared
        linkRelease instanceof LinkSharedLibrary
        linkRelease.binaryFile.get().asFile == projectDir.file("build/lib/main/release/shared/" + OperatingSystem.current().getSharedLibraryName("testLib"))
        !linkRelease.debuggable
    }

    def "output locations are calculated using base name defined on extension"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.library.baseName = "test_lib"

        then:
        def link = project.tasks.linkDebugShared
        link.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/shared/" + OperatingSystem.current().getSharedLibraryName("test_lib"))
    }

    def "output locations reflects changes to buildDir"() {
        given:
        project.pluginManager.apply(CppLibraryPlugin)

        when:
        project.buildDir = "output"

        then:
        def compileCpp = project.tasks.compileDebugSharedCpp
        compileCpp.objectFileDir.get().asFile == project.file("output/obj/main/debug/shared")

        def link = project.tasks.linkDebugShared
        link.outputFile == projectDir.file("output/lib/main/debug/shared/" + OperatingSystem.current().getSharedLibraryName("testLib"))
    }

    def "adds header zip task when maven-publish plugin is applied"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.pluginManager.apply(MavenPublishPlugin)

        then:
        def zip = project.tasks.cppHeaders
        zip instanceof Zip
    }

    def "adds publications when maven-publish plugin is applied"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.pluginManager.apply(MavenPublishPlugin)
        project.version = 1.2
        project.group = 'my.group'
        project.library.baseName = 'mylib'

        then:
        def publishing = project.publishing
        publishing.publications.size() == 5

        def main = publishing.publications.main
        main.groupId == 'my.group'
        main.artifactId == 'mylib'
        main.version == '1.2'
        main.artifacts.size() == 1

        def debugShared = publishing.publications.debugShared
        debugShared.groupId == 'my.group'
        debugShared.artifactId == 'mylib_debugShared'
        debugShared.version == '1.2'
        debugShared.artifacts.size() == expectedSharedLibFiles()

        def releaseShared = publishing.publications.releaseShared
        releaseShared.groupId == 'my.group'
        releaseShared.artifactId == 'mylib_releaseShared'
        releaseShared.version == '1.2'
        releaseShared.artifacts.size() == expectedSharedLibFiles()

        def debugStatic = publishing.publications.debugStatic
        debugStatic.groupId == 'my.group'
        debugStatic.artifactId == 'mylib_debugStatic'
        debugStatic.version == '1.2'
        debugStatic.artifacts.size() == 1

        def releaseStatic = publishing.publications.releaseStatic
        releaseStatic.groupId == 'my.group'
        releaseStatic.artifactId == 'mylib_releaseStatic'
        releaseStatic.version == '1.2'
        releaseStatic.artifacts.size() == 1
    }

    private int expectedSharedLibFiles() {
        OperatingSystem.current().windows ? 2 : 1
    }
}
