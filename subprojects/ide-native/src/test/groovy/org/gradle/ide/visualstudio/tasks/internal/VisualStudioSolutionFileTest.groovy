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
import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.ide.visualstudio.TextProvider
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfiguration
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class VisualStudioSolutionFileTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    def fileResolver = Mock(FileResolver)
    def instantiator = DirectInstantiator.INSTANCE
    def solutionFile = new VisualStudioSolutionFile()
    def binary1 = binary("one")

    def "setup"() {
        solutionFile.loadDefaults()
    }

    def "empty solution file"() {
        expect:
        generatedSolution.content ==
"""Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
	EndGlobalSection
	GlobalSection(SolutionProperties) = preSolution
		HideSolutionNode = FALSE
	EndGlobalSection
EndGlobal
"""
    }

    def "create for single project configuration"() {
        when:
        def project = createProject("project1")
        def configuration1 = createProjectConfiguration(project, "projectConfig")
        solutionFile.addSolutionConfiguration("solutionConfig", [configuration1])
        solutionFile.mainProject = project

        then:
        generatedSolution.content ==
"""Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project1", "${project.projectFile.location.absolutePath}", "${project.uuid}"
EndProject
Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
		solutionConfig=solutionConfig
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
		${project.uuid}.solutionConfig.ActiveCfg = projectConfig|Win32
		${project.uuid}.solutionConfig.Build.0 = projectConfig|Win32
	EndGlobalSection
	GlobalSection(SolutionProperties) = preSolution
		HideSolutionNode = FALSE
	EndGlobalSection
EndGlobal
"""
    }

    def "create for multiple configurations"() {
        when:
        def project1 = createProject("project1")
        def project2 = createProject("project2")
        solutionFile.mainProject = project1
        solutionFile.addSolutionConfiguration("solutionConfig1", [
                createProjectConfiguration(project1, "config1"),
                createProjectConfiguration(project1, "config2"),
                createProjectConfiguration(project2, "configA")
        ])
        solutionFile.addSolutionConfiguration("solutionConfig2", [
                createProjectConfiguration(project2, "configA")
        ])

        then:
        generatedSolution.content ==
"""Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project1", "${project1.projectFile.location.absolutePath}", "${project1.uuid}"
EndProject
Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project2", "${project2.projectFile.location.absolutePath}", "${project2.uuid}"
EndProject
Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
		solutionConfig1=solutionConfig1
		solutionConfig2=solutionConfig2
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
		${project1.uuid}.solutionConfig1.ActiveCfg = config1|Win32
		${project1.uuid}.solutionConfig1.Build.0 = config1|Win32
		${project1.uuid}.solutionConfig1.ActiveCfg = config2|Win32
		${project1.uuid}.solutionConfig1.Build.0 = config2|Win32
		${project2.uuid}.solutionConfig1.ActiveCfg = configA|Win32
		${project2.uuid}.solutionConfig2.ActiveCfg = configA|Win32
	EndGlobalSection
	GlobalSection(SolutionProperties) = preSolution
		HideSolutionNode = FALSE
	EndGlobalSection
EndGlobal
"""
    }

    def "applies multiple text actions"() {
        when:
        solutionFile.actions << ({ TextProvider text ->
            text.setText("foo")
        } as Action)
        solutionFile.actions << ({ TextProvider text ->
            text.asBuilder().append("bar")
        } as Action)

        then:
        generatedSolutionFile.text == "foobar"
    }

    def "can get and set text with actions"() {
        when:
        solutionFile.actions << ({ TextProvider text ->
            text.text = "test"
        } as Action)
        solutionFile.actions << ({ TextProvider text ->
            text.text = text.text.reverse()
        } as Action)

        then:
        generatedSolutionFile.text == "tset"
    }

    private VisualStudioProjectConfiguration createProjectConfiguration(DefaultVisualStudioProject project1, String configName) {
        return new VisualStudioProjectConfiguration(project1, configName, "Win32", binary1)
    }

    private DefaultVisualStudioProject createProject(String projectName) {
        final project1File = new File(projectName)
        fileResolver.resolve("${projectName}.vcxproj") >> project1File
        return new DefaultVisualStudioProject(projectName, binary1.component, fileResolver, instantiator)
    }

    private NativeBinarySpec binary(def name) {
        def component = Mock(NativeComponentSpec)
        def binary = Mock(NativeBinarySpecInternal)
        component.name >> "${name}Component"
        component.projectPath >> "project-path"
        binary.name >> name
        binary.component >> component
        return binary
    }

    private SolutionFile getGeneratedSolution() {
        TestFile file = getGeneratedSolutionFile()
        return new SolutionFile(file)
    }

    private TestFile getGeneratedSolutionFile() {
        def file = testDirectoryProvider.testDirectory.file("solution.txt")
        solutionFile.store(file)
        file
    }
}