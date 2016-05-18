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
import org.gradle.internal.xml.XmlTransformer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class VisualStudioProjectFileTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    Transformer<String, File> fileNameTransformer = { it.name } as Transformer<String, File>
    def generator = new VisualStudioProjectFile(new XmlTransformer(), fileNameTransformer)

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
        generator.gradleCommand = 'GRADLE'
        generator.addConfiguration(configuration("debugWin32", "Win32", ["foo", "bar"], ["include1", "include2"]))
        generator.addConfiguration(configuration("releaseWin32", "Win32", ["foo", "bar"], ["include1", "include2", "include3"]))
        generator.addConfiguration(configuration("debugX64", "x64", ["foo", "bar"], ["include1", "include2"]))

        then:
        final configurations = projectFile.projectConfigurations
        configurations.size() == 3
        with (configurations['debugWin32']) {
            name == 'debugWin32'
            platformName == 'Win32'
            macros == "foo;bar"
            includePath == "include1;include2"
            buildCommand == "GRADLE debugWin32Build"
        }
        with (configurations['releaseWin32']) {
            name == 'releaseWin32'
            platformName == 'Win32'
            macros == "foo;bar"
            includePath == "include1;include2;include3"
            buildCommand == "GRADLE releaseWin32Build"
        }
        with (configurations['debugX64']) {
            name == 'debugX64'
            platformName == 'x64'
            macros == "foo;bar"
            includePath == "include1;include2"
            buildCommand == "GRADLE debugX64Build"
        }
    }

    private VisualStudioProjectConfiguration configuration(def configName, def platformName, def defines, def includes) {
        return Stub(VisualStudioProjectConfiguration) {
            getName() >> "${configName}|${platformName}"
            getConfigurationName() >> configName
            getPlatformName() >> platformName
            getBuildTask() >> "${configName}Build"
            getCleanTask() >> "${configName}Clean"
            getCompilerDefines() >> defines
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
