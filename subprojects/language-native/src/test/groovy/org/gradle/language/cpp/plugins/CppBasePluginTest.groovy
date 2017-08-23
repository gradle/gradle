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
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class CppBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("test").build()

    def "adds compile task for component"() {
        def component = Stub(CppComponent)
        component.name >> name

        when:
        project.pluginManager.apply(CppBasePlugin)
        project.components.add(component)

        then:
        def compileCpp = project.tasks[taskName]
        compileCpp instanceof CppCompile
        compileCpp.objectFileDirectory.get().asFile == projectDir.file("build/${name}/objs")

        where:
        name   | taskName
        "main" | "compileCpp"
        "test" | "compileTestCpp"
    }

    def "adds link task for executable"() {
        def baseName = project.providers.property(String)
        baseName.set("test_app")
        def component = Stub(CppApplication)
        component.name >> name
        component.baseName >> baseName

        when:
        project.pluginManager.apply(CppBasePlugin)
        project.components.add(component)

        then:
        def link = project.tasks[taskName]
        link instanceof LinkExecutable
        link.binaryFile.get().asFile == projectDir.file("build/exe/" + OperatingSystem.current().getExecutableName("test_app"))

        where:
        name   | taskName
        "main" | "linkMain"
        "test" | "linkTest"
    }

    def "adds link task for shared library"() {
        def baseName = project.providers.property(String)
        baseName.set("test_lib")
        def component = Stub(CppLibrary)
        component.name >> name
        component.baseName >> baseName

        when:
        project.pluginManager.apply(CppBasePlugin)
        project.components.add(component)

        then:
        def link = project.tasks[taskName]
        link instanceof LinkSharedLibrary
        link.binaryFile.get().asFile == projectDir.file("build/lib/" + OperatingSystem.current().getSharedLibraryName("test_lib"))

        where:
        name   | taskName
        "main" | "linkMain"
        "test" | "linkTest"
    }
}
