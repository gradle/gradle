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

import org.gradle.ide.visualstudio.fixtures.SolutionFile
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class VisualStudioSolutionFileTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
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
        debug|Win32=debug|Win32
    EndGlobalSection
EndGlobal
"""
    }

    def "includes project references"() {
        when:
        def project1 = new VisualStudioProject("project1", null)
        def configuration1 = new VisualStudioProjectConfiguration(project1, null, "type")
        solutionFile.addProjectConfiguration(configuration1)

        def project2 = new VisualStudioProject("project2", null)
        def configuration2 = new VisualStudioProjectConfiguration(project2, null, "type")
        solutionFile.addProjectConfiguration(configuration2)

        then:
        generatedSolution.assertHasProjects(
                [name: "project1", file: "project1.vcxproj", uuid: project1.uuid],
                [name: "project2", file: "project2.vcxproj", uuid: project2.uuid]
        )
    }

    private SolutionFile getGeneratedSolution() {
        def file = testDirectoryProvider.testDirectory.file("solution.txt")
        solutionFile.store(file)
        return new SolutionFile(file)
    }
}