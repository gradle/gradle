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
import org.gradle.language.swift.SwiftComponent
import org.gradle.language.swift.SwiftApplication
import org.gradle.language.swift.SwiftLibrary
import org.gradle.language.swift.tasks.SwiftCompile
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftBasePluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("test").build()

    def "adds compile task for component"() {
        def component = Stub(SwiftComponent)
        component.name >> name
        component.module >> project.providers.property(String)

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(component)

        then:
        def compileSwift = project.tasks[taskName]
        compileSwift instanceof SwiftCompile
        compileSwift.objectFileDirectory.get().asFile == projectDir.file("build/${name}/objs")

        where:
        name   | taskName
        "main" | "compileSwift"
        "test" | "compileTestSwift"
    }

    def "adds link task for executable"() {
        def module = project.providers.property(String)
        module.set("TestApp")
        def component = Stub(SwiftApplication)
        component.name >> name
        component.module >> module

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(component)

        then:
        def link = project.tasks[taskName]
        link instanceof LinkExecutable
        link.binaryFile.get().asFile == projectDir.file("build/exe/" + OperatingSystem.current().getExecutableName("TestApp"))

        where:
        name   | taskName
        "main" | "linkMain"
        "test" | "linkTest"
    }

    def "adds link task for shared library"() {
        def module = project.providers.property(String)
        module.set("TestLib")
        def component = Stub(SwiftLibrary)
        component.name >> name
        component.module >> module

        when:
        project.pluginManager.apply(SwiftBasePlugin)
        project.components.add(component)

        then:
        def link = project.tasks[taskName]
        link instanceof LinkSharedLibrary
        link.binaryFile.get().asFile == projectDir.file("build/lib/" + OperatingSystem.current().getSharedLibraryName("TestLib"))

        where:
        name   | taskName
        "main" | "linkMain"
        "test" | "linkTest"
    }
}
