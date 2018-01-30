/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.integtests.fixtures.BuildOperationNotificationsFixture
import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Ignore

class BuildInitializationBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    final buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    @SuppressWarnings("GroovyUnusedDeclaration")
    final operationNotificationsFixture = new BuildOperationNotificationsFixture(executer, temporaryFolder)

    def "build operations are fired and build path is exposed"() {
        buildFile << """
            task foo {
                doLast {
                    println 'foo'
                }
            }
        """
        when:
        succeeds('foo')

        def loadBuildBuildOperation = buildOperations.first(LoadBuildBuildOperationType)
        def evaluateSettingsBuildOperation = buildOperations.first(EvaluateSettingsBuildOperationType)
        def configureBuildBuildOperations = buildOperations.first(ConfigureBuildBuildOperationType)
        def loadProjectsBuildOperation = buildOperations.first(LoadProjectsBuildOperationType)

        then:
        loadBuildBuildOperation.details.buildPath == ":"
        loadBuildBuildOperation.result.isEmpty()

        evaluateSettingsBuildOperation.details.buildPath == ":"
        evaluateSettingsBuildOperation.result.isEmpty()
        assert loadBuildBuildOperation.id == evaluateSettingsBuildOperation.parentId

        configureBuildBuildOperations.details.buildPath == ":"
        configureBuildBuildOperations.result.isEmpty()

        loadProjectsBuildOperation.details.buildPath == ":"
        loadProjectsBuildOperation.result.rootProject.projectDir == settingsFile.parent
        buildOperations.first('Configure build').id == loadProjectsBuildOperation.parentId
    }

    @Ignore("https://github.com/gradle/gradle/issues/3873")
    def "build operations for composite builds are fired and build path is exposed"() {
        buildFile << """
            apply plugin:'java'
            
            dependencies {
                compile 'org.acme:nested:+'
            }
        """
        def nestedSettings = file("nested/settings.gradle")
        nestedSettings.text = "rootProject.name = 'nested'"
        file("nested/build.gradle").text = """
            apply plugin: 'java'
            group = 'org.acme'
        """
        when:
        succeeds('build', '--include-build', 'nested')

        def loadBuildBuildOperations = buildOperations.all(LoadBuildBuildOperationType)
        def evaluateSettingsBuildOperations = buildOperations.all(EvaluateSettingsBuildOperationType)
        def configureBuildBuildOperations = buildOperations.all(ConfigureBuildBuildOperationType)
        def loadProjectsBuildOperations = buildOperations.all(LoadProjectsBuildOperationType)

        then:
        loadBuildBuildOperations*.details.buildPath == [':nested', ':']
        loadBuildBuildOperations*.result.isEmpty() == [true, true]

        evaluateSettingsBuildOperations*.details.buildPath == [':nested', ':']
        evaluateSettingsBuildOperations*.result.isEmpty() == [true, true]

        configureBuildBuildOperations*.details.buildPath == [':nested', ':']
        configureBuildBuildOperations*.result.isEmpty() == [true, true]

        loadProjectsBuildOperations*.details.buildPath == [':nested', ':']
        loadProjectsBuildOperations*.result.rootProject.projectDir == [nestedSettings.parent, settingsFile.parent]
    }

}
