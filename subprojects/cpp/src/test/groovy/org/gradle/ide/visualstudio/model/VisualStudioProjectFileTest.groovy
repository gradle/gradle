/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.model
import org.gradle.api.Transformer
import org.gradle.api.plugins.ExtensionAware
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.nativebinaries.NativeBinary
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class VisualStudioProjectFileTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    Transformer<String, File> fileNameTransformer = { it.name } as Transformer<String, File>
    def generator = new VisualStudioProjectFile(fileNameTransformer)

    def "setup"() {
        generator.loadDefaults()
    }

    def "empty project file"() {
        expect:
        projectFile.projectConfigurations.isEmpty()
        projectFile.sourceFiles == []
        projectFile.headerFiles == []
    }

    def "set project uuid"() {
        when:
        generator.setProjectUuid("THE_PROJECT_UUID")

        then:
        projectFile.projectGuid == "THE_PROJECT_UUID"
    }

    def "add source and headers"() {
        when:
        generator.addSourceFile(file("sourceOne"))
        generator.addSourceFile(file("sourceTwo"))

        generator.addHeaderFile(file("headerOne"))
        generator.addHeaderFile(file("headerTwo"))

        then:
        projectFile.sourceFiles == ["sourceOne", "sourceTwo"]
        projectFile.headerFiles == ["headerOne", "headerTwo"]
    }

    def "add configuration"() {
        when:
        def configuration = Mock(VisualStudioProjectConfiguration)
        configuration.configurationName >> "debug"
        configuration.platformName >> "Win32"
        configuration.buildTask >> "buildMe"
        configuration.cleanTask >> "cleanMe"
        configuration.defines >> ["foo", "bar"]
        configuration.includePaths >> [file("include1"), file("include2")]

        generator.addConfiguration(configuration)

        debugText()

        then:
        final configurations = projectFile.projectConfigurations
        configurations.size() == 1
        configurations[0].configName == 'debug'
        configurations[0].platformName == 'Win32'
        configurations[0].macros == "foo;bar"
        configurations[0].includePath == "include1;include2"
    }

    private ProjectFile getProjectFile() {
        def file = testDirectoryProvider.testDirectory.file("project.xml")
        generator.store(file)
        return new ProjectFile(file)
    }

    private void debugText() {
        def file = testDirectoryProvider.testDirectory.file("debug.xml")
        generator.store(file)
        println file.text
    }

    private TestFile file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }

    interface ExtensionAwareBinary extends NativeBinary, ExtensionAware {}
}