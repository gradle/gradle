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

package org.gradle.nativeplatform.test.cpp.plugins

import org.gradle.internal.os.OperatingSystem
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.plugins.CppApplicationPlugin
import org.gradle.language.cpp.plugins.CppLibraryPlugin
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.test.cpp.CppTestSuite
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class CppUnitTestPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testApp").build()

    def "adds extension with convention for source layout and module name"() {
        given:
        def src = projectDir.file("src/unitTest/cpp/test.cpp").createFile()

        when:
        project.pluginManager.apply(CppUnitTestPlugin)
        project.evaluate()

        then:
        project.unitTest instanceof CppTestSuite
        project.unitTest.baseName.get() == "unitTest"
        project.unitTest.cppSource.files == [src] as Set
    }

    def "sets tested component to main component when applying C++ library plugin"() {
        when:
        project.pluginManager.apply(CppUnitTestPlugin)

        then:
        project.unitTest.testedComponent.orNull == null

        when:
        project.pluginManager.apply(CppLibraryPlugin)
        project.evaluate()

        then:
        project.unitTest.testedComponent.orNull == project.library
    }

    def "sets tested component to main component when applying C++ application plugin"() {
        when:
        project.pluginManager.apply(CppUnitTestPlugin)

        then:
        project.unitTest.testedComponent.orNull == null

        when:
        project.pluginManager.apply(CppApplicationPlugin)
        project.evaluate()

        then:
        project.unitTest.testedComponent.orNull == project.application
    }

    def "registers a component for the test suite"() {
        when:
        project.pluginManager.apply(CppUnitTestPlugin)
        project.evaluate()

        then:
        project.components.unitTest == project.unitTest
        project.unitTest.testExecutable.name == 'unitTestExecutable'
        project.components.containsAll([project.unitTest.testExecutable])

        and:
        def binaries = [project.unitTest.testExecutable]
        binaries.findAll { it.debuggable && !it.optimized && it instanceof CppExecutable }.size() == 1

        and:
        project.unitTest.developmentBinary == binaries.find { it.debuggable && !it.optimized && it instanceof CppExecutable }
    }

    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/unitTest/cpp/test.cpp").createFile()

        when:
        project.pluginManager.apply(CppUnitTestPlugin)
        project.evaluate()

        then:
        def compileCpp = project.tasks.compileUnitTestCpp
        compileCpp instanceof CppCompile
        compileCpp.source.files == [src] as Set
        compileCpp.objectFileDir.get().asFile == projectDir.file("build/obj/unit/test")
        compileCpp.debuggable
        !compileCpp.optimized

        def link = project.tasks.linkUnitTest
        link instanceof LinkExecutable
        link.binaryFile.get().asFile == projectDir.file("build/exe/unit/test/" + OperatingSystem.current().getExecutableName("unitTest"))
        link.debuggable

        def install = project.tasks.installUnitTest
        install instanceof InstallExecutable
        install.installDirectory.get().asFile == project.file("build/install/unit/test")
        install.runScriptFile.get().asFile.name == OperatingSystem.current().getScriptName("unitTest")
    }

    def "output locations reflects changes to buildDir"() {
        when:
        project.pluginManager.apply(CppUnitTestPlugin)
        project.buildDir = project.file("output")
        project.evaluate()

        then:
        def compileCpp = project.tasks.compileUnitTestCpp
        compileCpp.objectFileDir.get().asFile == projectDir.file("output/obj/unit/test")

        def link = project.tasks.linkUnitTest
        link.binaryFile.get().asFile == projectDir.file("output/exe/unit/test/" + OperatingSystem.current().getExecutableName("unitTest"))

        def install = project.tasks.installUnitTest
        install.installDirectory.get().asFile == project.file("output/install/unit/test")
        install.runScriptFile.get().asFile.name == OperatingSystem.current().getScriptName("unitTest")
    }
}
