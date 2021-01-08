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
import org.gradle.ide.visualstudio.TextProvider
import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.ide.visualstudio.internal.VisualStudioProjectConfigurationMetadata
import org.gradle.ide.visualstudio.internal.VisualStudioProjectMetadata
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject.getUUID

class VisualStudioSolutionFileTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def solutionFile = new VisualStudioSolutionFile()

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
        def project = createProject("project1", ["projectConfig"])
        solutionFile.projects = [project]

        then:
        generatedSolution.content ==
"""Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project1", "${project.file.absolutePath}", "${getUUID(project.file)}"
EndProject
Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
		projectConfig|Win32 = projectConfig|Win32
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
		${getUUID(project.file)}.projectConfig|Win32.ActiveCfg = projectConfig|Win32
		${getUUID(project.file)}.projectConfig|Win32.Build.0 = projectConfig|Win32
	EndGlobalSection
	GlobalSection(SolutionProperties) = preSolution
		HideSolutionNode = FALSE
	EndGlobalSection
EndGlobal
"""
    }

    def "create for multiple configurations"() {
        when:
        def project1 = createProject("project1", ["config1", "config2"])
        def project2 = createProject("project2", ["config1", "configA"])
        solutionFile.projects = [project1, project2]

        then:
        generatedSolution.content ==
"""Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project1", "${project1.file.absolutePath}", "${getUUID(project1.file)}"
EndProject
Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project2", "${project2.file.absolutePath}", "${getUUID(project2.file)}"
EndProject
Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
		config1|Win32 = config1|Win32
		config2|Win32 = config2|Win32
		configA|Win32 = configA|Win32
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
		${getUUID(project1.file)}.config1|Win32.ActiveCfg = config1|Win32
		${getUUID(project1.file)}.config1|Win32.Build.0 = config1|Win32
		${getUUID(project1.file)}.config2|Win32.ActiveCfg = config2|Win32
		${getUUID(project1.file)}.config2|Win32.Build.0 = config2|Win32
		${getUUID(project1.file)}.configA|Win32.ActiveCfg = config2|Win32
		${getUUID(project2.file)}.config1|Win32.ActiveCfg = config1|Win32
		${getUUID(project2.file)}.config1|Win32.Build.0 = config1|Win32
		${getUUID(project2.file)}.config2|Win32.ActiveCfg = configA|Win32
		${getUUID(project2.file)}.configA|Win32.ActiveCfg = configA|Win32
		${getUUID(project2.file)}.configA|Win32.Build.0 = configA|Win32
	EndGlobalSection
	GlobalSection(SolutionProperties) = preSolution
		HideSolutionNode = FALSE
	EndGlobalSection
EndGlobal
"""
    }

    def "create for single unbuildable project configuration"() {
        when:
        def project = createProject("project1", ["unbuildable"], false)
        solutionFile.projects = [project]

        then:
        generatedSolution.content ==
                """Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project1", "${project.file.absolutePath}", "${getUUID(project.file)}"
EndProject
Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
		unbuildable|Win32 = unbuildable|Win32
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
		${getUUID(project.file)}.unbuildable|Win32.ActiveCfg = unbuildable|Win32
	EndGlobalSection
	GlobalSection(SolutionProperties) = preSolution
		HideSolutionNode = FALSE
	EndGlobalSection
EndGlobal
"""
    }

    def "create for multiple configurations with one unbuildable"() {
        when:
        def project1 = createProject("project1", ["config1", "config2"])
        def project2 = createProject("project2", ["unbuildable"], false)
        solutionFile.projects = [project1, project2]

        then:
        generatedSolution.content ==
                """Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project1", "${project1.file.absolutePath}", "${getUUID(project1.file)}"
EndProject
Project("{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942}") = "project2", "${project2.file.absolutePath}", "${getUUID(project2.file)}"
EndProject
Global
	GlobalSection(SolutionConfigurationPlatforms) = preSolution
		config1|Win32 = config1|Win32
		config2|Win32 = config2|Win32
		unbuildable|Win32 = unbuildable|Win32
	EndGlobalSection
	GlobalSection(ProjectConfigurationPlatforms) = postSolution
		${getUUID(project1.file)}.config1|Win32.ActiveCfg = config1|Win32
		${getUUID(project1.file)}.config1|Win32.Build.0 = config1|Win32
		${getUUID(project1.file)}.config2|Win32.ActiveCfg = config2|Win32
		${getUUID(project1.file)}.config2|Win32.Build.0 = config2|Win32
		${getUUID(project1.file)}.unbuildable|Win32.ActiveCfg = config2|Win32
		${getUUID(project2.file)}.config1|Win32.ActiveCfg = unbuildable|Win32
		${getUUID(project2.file)}.config2|Win32.ActiveCfg = unbuildable|Win32
		${getUUID(project2.file)}.unbuildable|Win32.ActiveCfg = unbuildable|Win32
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

    private VisualStudioProjectMetadata createProject(String projectName, List<String> configNames, boolean buildable = true) {
        final project1File = new File(projectName)
        def metadata = Stub(VisualStudioProjectMetadata)
        metadata.file >> project1File
        metadata.name >> projectName
        metadata.configurations >> configNames.collect {
            def configurationMetadata = Stub(VisualStudioProjectConfigurationMetadata)
            configurationMetadata.name >> "$it|Win32"
            configurationMetadata.buildable >> buildable
            return configurationMetadata
        }
        return metadata
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
