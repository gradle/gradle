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

package org.gradle.ide.visualstudio.fixtures

import org.gradle.plugins.ide.fixtures.IdeProjectFixture
import org.gradle.plugins.ide.fixtures.IdeWorkspaceFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil

class SolutionFile extends IdeWorkspaceFixture {
    TestFile file
    String content
    Map<String, ProjectReference> projects = [:]

    SolutionFile(TestFile solutionFile) {
        solutionFile.assertIsFile()
        this.file = solutionFile
        assert TextUtil.convertLineSeparators(solutionFile.text, TextUtil.windowsLineSeparator) == solutionFile.text : "Solution file contains non-windows line separators"

        content = TextUtil.normaliseLineSeparators(solutionFile.text)

        content.findAll(~/(?m)^Project\(\"\{8BC9CEB8-8B4A-11D0-8D11-00A0C91BC942\}\"\) = \"(\w+)\", \"([^\"]*)\", \"\{([\w\-]+)\}\"$/, {
            projects.put(it[1], new ProjectReference(it[1], it[2], it[3]))
        })
    }

    @Override
    void assertContains(IdeProjectFixture project) {
        assert project instanceof ProjectFile
        assert projects.keySet().contains(project.name)
        def ref = projects[project.name]
        assert ref.file == project.projectFile.absolutePath
    }

    def assertHasProjects(String... names) {
        return assertHasProjects(names as List)
    }

    def assertHasProjects(Iterable<String> names) {
        assert projects.keySet() == names as Set
        return true
    }

    def assertReferencesProject(ProjectFile expectedProject, Collection<String> configurations) {
        assertReferencesProject(expectedProject, configurations.collectEntries {[(it):it]})
    }

    def assertReferencesProject(ProjectFile expectedProject, Map<String, String> configurations) {
        assertReferencesProject(expectedProject.name, expectedProject, configurations)
    }

    def assertReferencesProject(String projectName, ProjectFile expectedProject, Map<String, String> configurations) {
        ProjectReference reference = projects.get(projectName)
        assert reference.uuid == expectedProject.projectGuid
        assert reference.file == expectedProject.projectFile.absolutePath
        assert reference.configurations == configurations
        return true
    }

    class ProjectReference {
        final String name
        final String file
        final String rawUuid

        ProjectReference(String name, String file, String rawUuid) {
            this.name = name
            this.file = file
            this.rawUuid = rawUuid
        }

        String getUuid() {
            return '{' + rawUuid + '}'
        }

        Map<String, String> getConfigurations() {
            def configurations = [:]
            content.eachMatch(~/\{${rawUuid}\}\.([\w\\-]+)\|\w+\.ActiveCfg = ([\w\\-]+)\|\w+/, {
                configurations[it[1]] = it[2]
            })
            return configurations
        }
    }
}
