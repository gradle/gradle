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

package org.gradle.ide.visualstudio.tasks.internal
import org.gradle.api.Transformer
import org.gradle.ide.visualstudio.fixtures.ProjectFile
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfiguration
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

    def "add configurations"() {
        when:
        generator.addConfiguration(configuration("debug", "Win32", ["foo", "bar"], ["include1", "include2"]))
        generator.addConfiguration(configuration("release", "Win32", ["foo", "bar"], ["include1", "include2", "include3"]))
        generator.addConfiguration(configuration("debug", "x64", ["foo", "bar"], ["include1", "include2"]))

        then:
        final configurations = projectFile.projectConfigurations
        configurations.size() == 3
        with (configurations['debug|Win32']) {
            configName == 'debug'
            platformName == 'Win32'
            macros == "foo;bar"
            includePath == "include1;include2"
        }
        with (configurations['release|Win32']) {
            configName == 'release'
            platformName == 'Win32'
            macros == "foo;bar"
            includePath == "include1;include2;include3"
        }
        with (configurations['debug|x64']) {
            configName == 'debug'
            platformName == 'x64'
            macros == "foo;bar"
            includePath == "include1;include2"
        }
    }

    private VisualStudioProjectConfiguration configuration(def configName, def platformName, def defines, def includes) {
        return Stub(VisualStudioProjectConfiguration) {
            getName() >> "${configName}|${platformName}"
            getConfigurationName() >> configName
            getPlatformName() >> platformName
            getBuildTask() >> "buildMe"
            getCleanTask() >> "cleanMe"
            getDefines() >> defines
            getIncludePaths() >> includes.collect { file(it) }
        }
    }

    private ProjectFile getProjectFile() {
        def file = testDirectoryProvider.testDirectory.file("project.xml")
        generator.store(file)
        return new ProjectFile(file)
    }

    private TestFile file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }
}