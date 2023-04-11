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

import org.gradle.api.ProjectConfigurationException
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.CppSharedLibrary
import org.gradle.language.cpp.CppStaticLibrary
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.Linkage
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class CppLibraryPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
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

    def "registers a component for the library with default linkage"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.evaluate()

        then:
        project.components.main == project.library
        project.library.binaries.get().name == ['mainDebug', 'mainRelease']
        project.components.containsAll(project.library.binaries.get())

        and:
        def binaries = project.library.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof CppSharedLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof CppSharedLibrary }.size() == 1

        and:
        project.library.developmentBinary.get() == binaries.find { it.debuggable && !it.optimized && it instanceof CppSharedLibrary }
    }

    def "registers a component for the library with static linkage"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.library.linkage = [Linkage.STATIC]
        project.evaluate()

        then:
        project.components.main == project.library
        project.library.binaries.get().name == ['mainDebug', 'mainRelease']
        project.components.containsAll(project.library.binaries.get())

        and:
        def binaries = project.library.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof CppStaticLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof CppStaticLibrary }.size() == 1

        and:
        project.library.developmentBinary.get() == binaries.find { it.debuggable && !it.optimized && it instanceof CppStaticLibrary }
    }

    def "registers a component for the library with both linkage"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.library.linkage = [Linkage.SHARED, Linkage.STATIC]
        project.evaluate()

        then:
        project.components.main == project.library
        project.library.binaries.get().name as Set == ['mainDebugShared', 'mainReleaseShared', 'mainDebugStatic', 'mainReleaseStatic'] as Set
        project.components.containsAll(project.library.binaries.get())

        and:
        def binaries = project.library.binaries.get()
        binaries.findAll { it.debuggable && it.optimized && it instanceof CppSharedLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof CppSharedLibrary }.size() == 1
        binaries.findAll { it.debuggable && it.optimized && it instanceof CppStaticLibrary }.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof CppStaticLibrary }.size() == 1

        and:
        project.library.developmentBinary.get() == binaries.find { it.debuggable && !it.optimized && it instanceof CppSharedLibrary }
    }

    def "adds compile and link tasks for default linkage"() {
        given:
        def src = projectDir.file("src/main/cpp/lib.cpp").createFile()
        def publicHeaders = projectDir.file("src/main/public").createDir()
        def privateHeaders = projectDir.file("src/main/headers").createDir()

        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.evaluate()

        then:
        def compileDebugCpp = project.tasks.compileDebugCpp
        compileDebugCpp instanceof CppCompile
        compileDebugCpp.includes.files.take(2) as List == [publicHeaders, privateHeaders]
        compileDebugCpp.source.files as List == [src]
        compileDebugCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug")
        compileDebugCpp.debuggable
        !compileDebugCpp.optimized

        def linkDebug = project.tasks.linkDebug
        linkDebug instanceof LinkSharedLibrary
        linkDebug.linkedFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("testLib"))
        linkDebug.debuggable

        def compileReleaseCpp = project.tasks.compileReleaseCpp
        compileReleaseCpp instanceof CppCompile
        compileReleaseCpp.includes.files.take(2) as List == [publicHeaders, privateHeaders]
        compileReleaseCpp.source.files as List == [src]
        compileReleaseCpp.objectFileDir.get().asFile == projectDir.file("build/obj/main/release")
        compileReleaseCpp.debuggable
        compileReleaseCpp.optimized

        def linkRelease = project.tasks.linkRelease
        linkRelease instanceof LinkSharedLibrary
        linkRelease.linkedFile.get().asFile == projectDir.file("build/lib/main/release/" + OperatingSystem.current().getSharedLibraryName("testLib"))
        linkRelease.debuggable
    }

    def "adds compile and link tasks for both linkage"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.library.linkage = [Linkage.SHARED, Linkage.STATIC]
        project.evaluate()

        then:
        def compileDebug = project.tasks.compileDebugSharedCpp
        compileDebug instanceof CppCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug/shared")
        compileDebug.debuggable
        !compileDebug.optimized

        def linkDebug = project.tasks.linkDebugShared
        linkDebug instanceof LinkSharedLibrary
        linkDebug.linkedFile.get().asFile == projectDir.file("build/lib/main/debug/shared/" + OperatingSystem.current().getSharedLibraryName("testLib"))
        linkDebug.debuggable

        def compileRelease = project.tasks.compileReleaseSharedCpp
        compileRelease instanceof CppCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDir.get().asFile == projectDir.file("build/obj/main/release/shared")
        compileRelease.debuggable
        compileRelease.optimized

        def linkRelease = project.tasks.linkReleaseShared
        linkRelease instanceof LinkSharedLibrary
        linkRelease.linkedFile.get().asFile == projectDir.file("build/lib/main/release/shared/" + OperatingSystem.current().getSharedLibraryName("testLib"))
        linkRelease.debuggable

        and:
        def compileDebugStatic = project.tasks.compileDebugStaticCpp
        compileDebugStatic instanceof CppCompile
        compileDebugStatic.source.files == [src] as Set
        compileDebugStatic.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug/static")
        compileDebugStatic.debuggable
        !compileDebugStatic.optimized

        def createDebugStatic = project.tasks.createDebugStatic
        createDebugStatic instanceof CreateStaticLibrary
        createDebugStatic.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/static/" + OperatingSystem.current().getStaticLibraryName("testLib"))

        def compileReleaseStatic = project.tasks.compileReleaseStaticCpp
        compileReleaseStatic instanceof CppCompile
        compileReleaseStatic.source.files == [src] as Set
        compileReleaseStatic.objectFileDir.get().asFile == projectDir.file("build/obj/main/release/static")
        compileReleaseStatic.debuggable
        compileReleaseStatic.optimized

        def createReleaseStatic = project.tasks.createReleaseStatic
        createReleaseStatic instanceof CreateStaticLibrary
        createReleaseStatic.binaryFile.get().asFile == projectDir.file("build/lib/main/release/static/" + OperatingSystem.current().getStaticLibraryName("testLib"))
    }

    def "adds compile and link tasks for static linkage only"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.library.linkage = [Linkage.STATIC]
        project.evaluate()

        then:
        project.tasks.withType(CppCompile)*.name == ['compileDebugCpp', 'compileReleaseCpp']
        project.tasks.withType(LinkSharedLibrary).empty

        and:
        def compileDebug = project.tasks.compileDebugCpp
        compileDebug instanceof CppCompile
        compileDebug.source.files == [src] as Set
        compileDebug.objectFileDir.get().asFile == projectDir.file("build/obj/main/debug")
        compileDebug.debuggable
        !compileDebug.optimized

        def createDebug = project.tasks.createDebug
        createDebug instanceof CreateStaticLibrary
        createDebug.binaryFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getStaticLibraryName("testLib"))

        def compileRelease = project.tasks.compileReleaseCpp
        compileRelease instanceof CppCompile
        compileRelease.source.files == [src] as Set
        compileRelease.objectFileDir.get().asFile == projectDir.file("build/obj/main/release")
        compileRelease.debuggable
        compileRelease.optimized

        def createRelease = project.tasks.createRelease
        createRelease instanceof CreateStaticLibrary
        createRelease.binaryFile.get().asFile == projectDir.file("build/lib/main/release/" + OperatingSystem.current().getStaticLibraryName("testLib"))
    }

    def "cannot change the library linkages after binaries have been calculated"() {
        given:
        project.pluginManager.apply(CppLibraryPlugin)
        project.library.linkage = [Linkage.STATIC]
        project.library.binaries.configureEach {
            project.library.linkage.add(Linkage.SHARED)
        }

        when:
        project.evaluate()

        then:
        def e = thrown(ProjectConfigurationException)
        e.cause instanceof IllegalStateException
        e.cause.message == "The value for property 'linkage' is final and cannot be changed any further."
    }

    def "output locations are calculated using base name defined on extension"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.library.baseName = "test_lib"
        project.evaluate()

        then:
        def link = project.tasks.linkDebug
        link.linkedFile.get().asFile == projectDir.file("build/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("test_lib"))
    }

    def "output locations reflects changes to buildDir"() {
        given:
        project.pluginManager.apply(CppLibraryPlugin)
        project.evaluate()

        when:
        project.buildDir = "output"

        then:
        def compileCpp = project.tasks.compileDebugCpp
        compileCpp.objectFileDir.get().asFile == project.file("output/obj/main/debug")

        def link = project.tasks.linkDebug
        link.linkedFile.get().asFile == projectDir.file("output/lib/main/debug/" + OperatingSystem.current().getSharedLibraryName("testLib"))
    }

    def "adds header zip task when maven-publish plugin is applied"() {
        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.pluginManager.apply(MavenPublishPlugin)
        project.evaluate()

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
        project.evaluate()

        then:
        def publishing = project.publishing
        publishing.publications.size() == 3

        def main = publishing.publications.main
        main.groupId == 'my.group'
        main.artifactId == 'mylib'
        main.version == '1.2'
        main.artifacts.size() == 1

        def debug = publishing.publications.mainDebug
        debug.groupId == 'my.group'
        debug.artifactId == 'mylib_debug'
        debug.version == '1.2'
        debug.artifacts.size() == expectedSharedLibFiles()

        def release = publishing.publications.mainRelease
        release.groupId == 'my.group'
        release.artifactId == 'mylib_release'
        release.version == '1.2'
        release.artifacts.size() == expectedSharedLibFiles()
    }

    private int expectedSharedLibFiles() {
        OperatingSystem.current().windows ? 2 : 1
    }
}
