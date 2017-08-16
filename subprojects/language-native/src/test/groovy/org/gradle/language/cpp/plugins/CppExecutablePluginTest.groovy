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

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.CppComponent
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
        project.executable instanceof CppComponent
        project.executable.baseName.get() == "testApp"
        project.executable.cppSource.files == [src] as Set
    }

    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppExecutablePlugin)

        then:
        def compileCpp = project.tasks.compileCpp
        compileCpp instanceof CppCompile
        compileCpp.includes.files == [project.file("src/main/headers")] as Set
        compileCpp.source.files == [src] as Set
        compileCpp.objectFileDirectory.get().get() == projectDir.file("build/main/objs")

        def link = project.tasks.linkMain
        link instanceof LinkExecutable
        link.binaryFile.get().get() == projectDir.file("build/exe/" + OperatingSystem.current().getExecutableName("testApp"))

        def install = project.tasks.installMain
        install instanceof InstallExecutable
        install.installDirectory.get().get() == projectDir.file("build/install/testApp")
        install.runScript.name == OperatingSystem.current().getScriptName("testApp")
    }

    def "output locations are calculated using base name defined on extension"() {
        when:
        project.pluginManager.apply(CppExecutablePlugin)
        project.executable.baseName.set("test_app")

        then:
        def link = project.tasks.linkMain
        link.binaryFile.get().get() == projectDir.file("build/exe/" + OperatingSystem.current().getExecutableName("test_app"))

        def install = project.tasks.installMain
        install.installDirectory.get().get() == projectDir.file("build/install/test_app")
        install.runScript.name == OperatingSystem.current().getScriptName("test_app")
    }

    def "output locations reflects changes to buildDir"() {
        given:
        project.pluginManager.apply(CppExecutablePlugin)

        expect:
        def compileCpp = project.tasks.compileCpp
        compileCpp.objectFileDir == project.file("build/main/objs")

        def link = project.tasks.linkMain
        link.outputFile.parentFile == project.file("build/exe")

        def install = project.tasks.installMain
        install.destinationDir == project.file("build/install/testApp")

        project.setBuildDir("output")

        compileCpp.objectFileDir == project.file("output/main/objs")
        link.outputFile.parentFile == project.file("output/exe")
        install.destinationDir == project.file("output/install/testApp")
        install.executable == link.outputFile

        link.setOutputFile(project.file("exe"))
        install.executable == link.outputFile
    }
}
