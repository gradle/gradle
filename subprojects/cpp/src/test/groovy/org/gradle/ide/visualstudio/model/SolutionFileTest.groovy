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

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class SolutionFileTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    def solutionFile = new SolutionFile()

    def "setup"() {
        solutionFile.loadDefaults()
        solutionFile.uuid = "THE_SOLUTION_UUID"
    }

    def "empty solution file"() {
        expect:
        solutionFileContent.text ==
"""Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Global
    GlobalSection(SolutionConfigurationPlatforms) = preSolution
        debug|Win32=debug|Win32
    EndGlobalSection
EndGlobal
"""
    }

    def "includes project references"() {
        when:
        def project1 = Mock(VisualStudioProject)
        project1.name >> "project_name"
        project1.projectFile >> "project_file"
        project1.uuid >> "project_uuid"
        solutionFile.addProject(project1)
        def project2 = Mock(VisualStudioProject)
        project2.name >> "project2_name"
        project2.projectFile >> "project2_file"
        project2.uuid >> "project2_uuid"
        solutionFile.addProject(project2)

        then:
        solutionFileContent.text ==
"""Microsoft Visual Studio Solution File, Format Version 11.00
# Visual C++ Express 2010

Project("THE_SOLUTION_UUID") = "project_name", "project_file", "project_uuid"
EndProject

Project("THE_SOLUTION_UUID") = "project2_name", "project2_file", "project2_uuid"
EndProject

Global
    GlobalSection(SolutionConfigurationPlatforms) = preSolution
        debug|Win32=debug|Win32
    EndGlobalSection
EndGlobal
"""
    }

    private TestFile getSolutionFileContent() {
        def file = testDirectoryProvider.testDirectory.file("solution.txt")
        solutionFile.store(file)
        return file
    }
}