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

import org.gradle.language.cpp.CppComponent
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class CppExecutablePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = TestUtil.createRootProject(projectDir)

    def "adds extension with convention for source layout"() {
        given:
        def src = projectDir.file("src/main/cpp/main.cpp").createFile()

        when:
        project.pluginManager.apply(CppExecutablePlugin)

        then:
        project.executable instanceof CppComponent
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

        def link = project.tasks.linkMain
        link instanceof LinkExecutable

        def install = project.tasks.installMain
        install instanceof InstallExecutable
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
        install.destinationDir == project.file("build/install/test")

        project.setBuildDir("output")

        compileCpp.objectFileDir == project.file("output/main/objs")
        link.outputFile.parentFile == project.file("output/exe")
        install.destinationDir == project.file("output/install/test")
        install.executable == link.outputFile

        link.setOutputFile(project.file("exe"))
        install.executable == link.outputFile
    }
}
