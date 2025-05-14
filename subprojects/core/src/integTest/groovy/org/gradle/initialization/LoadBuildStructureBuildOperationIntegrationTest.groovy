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

package org.gradle.initialization

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture

class LoadBuildStructureBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "multiproject settings with customizations are exposed correctly"() {
        createDirs("b", "a", "a/c", "d")
        settingsFile << """
        include "b"
        include "a"
        include "a:c"
        include "a:c:d"

        findProject(':a:c:d').projectDir = file("d")
        findProject(':a:c:d').buildFileName = "d.gradle"

        rootProject.name = "root"
        rootProject.buildFileName = 'root.gradle'
        """

        when:
        succeeds('help')

        then:
        def loadProjectsOp = buildOperations.only(LoadProjectsBuildOperationType)
        loadProjectsOp.result.buildPath == ":"
        def rootProject = loadProjectsOp.result.rootProject
        rootProject.path == ":"

        verifyProject(rootProject, 'root', ':', [':a', ':b'], testDirectory, 'root.gradle')
        verifyProject(project(':a', rootProject), 'a', ':a', [':a:c'])
        verifyProject(project(':b', rootProject), 'b')
        verifyProject(project(':a:c', rootProject), 'c', ':a:c', [':a:c:d'], testDirectory.file('a/c'))
        verifyProject(project(':a:c:d', rootProject), 'd', ':a:c:d', [], testDirectory.file('d'), 'd.gradle')

        def events = buildOperations.progress(ProjectsIdentifiedProgressDetails)
        events.size() == 1
        def identityProjectsEvent = events[0]
        identityProjectsEvent.details.buildPath == ":"

        def eventRootProject = identityProjectsEvent.details.rootProject
        verifyProject(eventRootProject, 'root', ':', [':a', ':b'], testDirectory, 'root.gradle')
        verifyProject(project(':a', eventRootProject), 'a', ':a', [':a:c'])
        verifyProject(project(':b', eventRootProject), 'b')
        verifyProject(project(':a:c', eventRootProject), 'c', ':a:c', [':a:c:d'], testDirectory.file('a/c'))
        verifyProject(project(':a:c:d', eventRootProject), 'd', ':a:c:d', [], testDirectory.file('d'), 'd.gradle')
    }

    def "composite participants expose their project structure"() {
        createDirs("a", "nested", "nested/b")
        settingsFile << """
        include "a"
        includeBuild "nested"

        rootProject.name = "root"
        rootProject.buildFileName = 'root.gradle'

        """

        file("nested/settings.gradle") << """
        rootProject.name = "nested"
        include "b"
        """

        file("nested/build.gradle") << """
        group = "org.acme"
        version = "1.0"
        """

        when:
        succeeds('help')


        then:
        def buildOperations = buildOperations.all(LoadProjectsBuildOperationType)
        buildOperations.size() == 2

        def rootBuildOperation = buildOperations[0]
        rootBuildOperation.result.buildPath == ":"

        def rootProject = rootBuildOperation.result.rootProject
        rootProject.path == ":"
        verifyProject(rootProject, 'root', ':', [':a'], testDirectory, 'root.gradle')
        verifyProject(project(":a", rootProject), 'a')

        def nestedBuildOperation = buildOperations[1]
        nestedBuildOperation.result.buildPath == ":nested"

        def nestedRootProject = nestedBuildOperation.result.rootProject
        nestedRootProject.path == ":"
        verifyProject(nestedRootProject, 'nested', ':nested', [':b'], testDirectory.file('nested'))
        verifyProject(project(":b", nestedRootProject), 'b', ':nested:b', [], testDirectory.file('nested/b'))
    }

    private void verifyProject(def project, String name, String buildTreePath = null, List<String> children = [], File projectDir = testDirectory.file(name), String buildFileName = 'build.gradle') {
        assert project.name == name
        assert project.getBuildTreePath == buildTreePath ?: project.path
        assert project.buildTreePath == buildTreePath ?: project.path
        assert project.projectDir == projectDir.absolutePath
        assert project.children*.path == children
        assert project.buildTreePath == buildTreePath ?: project.path
    }

    Map project(String path, Map rootProject, Map parent = null) {
        if (parent == null) {
            if (path.lastIndexOf(':') == 0) {
                return rootProject.children.find { it.path == path }
            } else {
                return project(path, rootProject, project(path.substring(0, path.lastIndexOf(':')), rootProject))
            }
        }
        return parent.children.find { it.path == path }
    }
}
