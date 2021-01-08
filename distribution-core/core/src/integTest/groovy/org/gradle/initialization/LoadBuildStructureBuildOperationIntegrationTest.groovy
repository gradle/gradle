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
import org.gradle.internal.operations.trace.BuildOperationRecord

class LoadBuildStructureBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "multiproject settings with customizations are are exposed correctly"() {
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
        opResult.buildPath == ":"
        opResult.rootProject.path == ":"

        verifyProject(opResult.rootProject, 'root', ':', [':a', ':b'], testDirectory, 'root.gradle')
        verifyProject(project(':a'), 'a', ':a', [':a:c'])
        verifyProject(project(':b'), 'b')
        verifyProject(project(':a:c'), 'c', ':a:c', [':a:c:d'], testDirectory.file('a/c'))
        verifyProject(project(':a:c:d'), 'd', ':a:c:d', [], testDirectory.file('d'), 'd.gradle')
    }

    def "settings set via cmdline flag are exposed correctly"() {
        def customSettingsDir = file("custom")
        customSettingsDir.mkdirs()
        def customSettingsFile = new File(customSettingsDir, "settings.gradle")
        customSettingsFile << """
        rootProject.name = "root"
        rootProject.buildFileName = 'root.gradle'

        include "a"
        """

        when:
        executer.withArguments("--settings-file", customSettingsFile.absolutePath)
        succeeds('help')

        then:
        operation().result.buildPath == ":"
        operation().result.rootProject.name == "root"
        operation().result.rootProject.path == ":"
        operation().result.rootProject.projectDir == customSettingsDir.absolutePath
        operation().result.rootProject.buildFile == customSettingsDir.file("root.gradle").absolutePath

        opResult.buildPath == ":"
        opResult.rootProject.path == ":"
        verifyProject(opResult.rootProject, 'root', ':', [':a'], customSettingsDir, 'root.gradle')
        verifyProject(project(':a'), 'a', ':a', [], customSettingsDir.file('a'))
    }

    def "composite participants expose their project structure"() {
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
        def buildOperations = operations()
        buildOperations.size() == 2

        def rootBuildOperation = operations()[0]
        rootBuildOperation.result.buildPath == ":"
        rootBuildOperation.result.rootProject.path == ":"
        verifyProject(rootBuildOperation.result.rootProject, 'root', ':', [':a'], testDirectory, 'root.gradle')
        verifyProject(project(":a", rootBuildOperation), 'a')

        def nestedBuildOperation = operations()[1]
        nestedBuildOperation.result.buildPath == ":nested"
        nestedBuildOperation.result.rootProject.path == ":"
        verifyProject(nestedBuildOperation.result.rootProject, 'nested', ':nested', [':b'], testDirectory.file('nested'))
        verifyProject(project(":b", nestedBuildOperation), 'b', ':nested:b', [], testDirectory.file('nested/b'))
    }

    private void verifyProject(def project, String name, String identityPath = null, List<String> children = [], File projectDir = testDirectory.file(name), String buildFileName = 'build.gradle') {
        assert project.name == name
        assert project.identityPath == identityPath ?: project.path
        assert project.projectDir == projectDir.absolutePath
        assert project.buildFile == new File(projectDir, buildFileName).absolutePath
        assert project.children*.path == children
    }

    def project(String path, BuildOperationRecord operation = operation(), Map parent = null) {
        if (parent == null) {
            if (path.lastIndexOf(':') == 0) {
                return operation.result.rootProject.children.find { it.path == path }
            } else {
                return project(path, operation, project(path.substring(0, path.lastIndexOf(':'))))
            }
        }
        return parent.children.find { it.path == path }
    }

    private BuildOperationRecord operation(){
        def operationRecords = operations()
        assert operationRecords.size() == 1
        operationRecords.iterator().next()
    }

    private getOpResult() {
        operation().result
    }
    private List<BuildOperationRecord> operations() {
        buildOperations.all(LoadProjectsBuildOperationType)
    }
}
