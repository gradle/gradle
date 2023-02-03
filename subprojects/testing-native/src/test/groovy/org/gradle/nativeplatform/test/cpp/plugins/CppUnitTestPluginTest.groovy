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
import org.gradle.language.cpp.plugins.CppApplicationPlugin
import org.gradle.language.cpp.plugins.CppLibraryPlugin
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.test.cpp.CppTestExecutable
import org.gradle.nativeplatform.test.cpp.CppTestSuite
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class CppUnitTestPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("someApp").build()

    def "adds extension with convention for source layout and module name"() {
        given:
        def src = projectDir.file("src/test/cpp/test.cpp").createFile()

        when:
        project.pluginManager.apply(CppUnitTestPlugin)
        project.evaluate()

        then:
        project.unitTest instanceof CppTestSuite
        project.unitTest.baseName.get() == "someAppTest"
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
        project.components.test == project.unitTest
        project.unitTest.binaries.get().name == ['testExecutable']
        project.components.containsAll project.unitTest.binaries.get()

        and:
        def binaries = project.unitTest.binaries.get()
        binaries.size() == 1
        binaries.findAll { it.debuggable && !it.optimized && it instanceof CppTestExecutable }.size() == 1

        and:
        project.unitTest.testBinary.get() == binaries.first()
    }

    def "adds compile, link and install tasks"() {
        given:
        def src = projectDir.file("src/test/cpp/test.cpp").createFile()

        when:
        project.pluginManager.apply(CppUnitTestPlugin)
        project.evaluate()

        then:
        def compileCpp = project.tasks.compileTestCpp
        compileCpp instanceof CppCompile
        compileCpp.source.files == [src] as Set
        compileCpp.objectFileDir.get().asFile == projectDir.file("build/obj/test")
        compileCpp.debuggable
        !compileCpp.optimized

        def link = project.tasks.linkTest
        link instanceof LinkExecutable
        link.linkedFile.get().asFile == projectDir.file("build/exe/test/" + OperatingSystem.current().getExecutableName("someAppTest"))
        link.debuggable

        def install = project.tasks.installTest
        install instanceof InstallExecutable
        install.installDirectory.get().asFile == project.file("build/install/test")
        install.runScriptFile.get().asFile.name == OperatingSystem.current().getScriptName("someAppTest")
    }

    def "output locations reflects changes to buildDir"() {
        when:
        project.pluginManager.apply(CppUnitTestPlugin)
        project.pluginManager.apply(CppApplicationPlugin)
        project.buildDir = project.file("output")
        project.evaluate()

        then:
        def compileCpp = project.tasks.compileTestCpp
        compileCpp.objectFileDir.get().asFile == projectDir.file("output/obj/test")

        def link = project.tasks.linkTest
        link.linkedFile.get().asFile == projectDir.file("output/exe/test/" + OperatingSystem.current().getExecutableName("someAppTest"))

        def install = project.tasks.installTest
        install.installDirectory.get().asFile == project.file("output/install/test")
        install.runScriptFile.get().asFile.name == OperatingSystem.current().getScriptName("someAppTest")
    }
}
