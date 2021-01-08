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

    def "operations are fired for complex nest of builds"() {
        settingsFile << """
            rootProject.name = "root-changed"
            includeBuild 'nested'
        """
        buildFile << """
            apply plugin:'java'
            dependencies {
                implementation 'org.acme:nested-changed:+'
            }
        """

        createDir("buildSrc") {
            file("settings.gradle") << "rootProject.name = 'buildsrc-changed'"
            file('build.gradle') << ""
            dir("buildSrc") {
                file("settings.gradle") << "rootProject.name = 'buildsrc-buildsrc-changed'"
                file('build.gradle') << ""
            }
        }

        createDir("nested") {
            file("settings.gradle") << """
                rootProject.name = "nested-changed"
                includeBuild "nested-nested"
            """
            file("build.gradle") << """
                apply plugin: 'java-library'
                group = 'org.acme'
            """
            dir("buildSrc") {
                file("settings.gradle") << "rootProject.name = 'nested-buildsrc-changed'"
                file('build.gradle') << ""
                dir("buildSrc") {
                    file("settings.gradle") << "rootProject.name = 'nested-buildsrc-buildsrc-changed'"
                    file('build.gradle') << ""
                }
            }

            dir("nested-nested") {
                file("settings.gradle") << """ rootProject.name = "nested-nested-changed" """
                dir("buildSrc") {
                    file("settings.gradle") << "rootProject.name = 'nested-nested-buildsrc-changed'"
                    file('build.gradle') << ""
                    dir("buildSrc") {
                        file("settings.gradle") << "rootProject.name = 'nested-nested-buildsrc-buildsrc-changed'"
                        file('build.gradle') << ""
                    }
                }
            }
        }

        createDir("nested-cli") {
            file("settings.gradle") << """
                rootProject.name = "nested-cli-changed"
                includeBuild "nested-cli-nested"
            """
            dir("buildSrc") {
                file("settings.gradle") << "rootProject.name = 'nested-cli-buildsrc-changed'"
                file('build.gradle') << ""
                dir("buildSrc") {
                    file("settings.gradle") << "rootProject.name = 'nested-cli-buildsrc-buildsrc-changed'"
                    file('build.gradle') << ""
                }
            }

            dir("nested-cli-nested") {
                file("settings.gradle") << """ rootProject.name = "nested-cli-nested-changed" """
                dir("buildSrc") {
                    file("settings.gradle") << "rootProject.name = 'nested-cli-nested-buildsrc-changed'"
                    file('build.gradle') << ""
                    dir("buildSrc") {
                        file("settings.gradle") << "rootProject.name = 'nested-cli-nested-buildsrc-buildsrc-changed'"
                        file('build.gradle') << ""
                    }
                }
            }
        }

        when:
        succeeds('build', '--include-build', 'nested-cli')

        then:
        def loadBuildBuildOperations = buildOperations.all(LoadBuildBuildOperationType)
        loadBuildBuildOperations*.details.buildPath == [
            ":",
            ":nested",
            ":nested-nested",
            ":nested-cli",
            ":nested-cli-nested",
            ":nested-nested:buildSrc",
            ":nested-nested:buildSrc:buildSrc",
            ":nested:buildSrc",
            ":nested:buildSrc:buildSrc",
            ":nested-cli-nested:buildSrc",
            ":nested-cli-nested:buildSrc:buildSrc",
            ":nested-cli:buildSrc",
            ":nested-cli:buildSrc:buildSrc",
            ":buildSrc",
            ":buildSrc:buildSrc",
        ]
        loadBuildBuildOperations*.details.includedBy == [
            null,
            ":",
            ":nested",
            ":",
            ":nested-cli",
            ":nested-nested",
            ":nested-nested:buildSrc",
            ":nested",
            ":nested:buildSrc",
            ":nested-cli-nested",
            ":nested-cli-nested:buildSrc",
            ":nested-cli",
            ":nested-cli:buildSrc",
            ":",
            ":buildSrc",
        ]

        def evaluateSettingsBuildOperations = buildOperations.all(EvaluateSettingsBuildOperationType)
        evaluateSettingsBuildOperations*.details.buildPath == [
            ":",
            ":nested",
            ":nested-nested",
            ":nested-cli",
            ":nested-cli-nested",
            ":nested-nested:buildSrc",
            ":nested-nested:buildSrc:buildSrc",
            ":nested:buildSrc",
            ":nested:buildSrc:buildSrc",
            ":nested-cli-nested:buildSrc",
            ":nested-cli-nested:buildSrc:buildSrc",
            ":nested-cli:buildSrc",
            ":nested-cli:buildSrc:buildSrc",
            ":buildSrc",
            ":buildSrc:buildSrc",
        ]

        def configureOrder = [
                ":",
                ":nested-nested",
                ":nested-nested:buildSrc",
                ":nested-nested:buildSrc:buildSrc",
                ":nested",
                ":nested:buildSrc",
                ":nested:buildSrc:buildSrc",
                ":nested-cli-nested",
                ":nested-cli-nested:buildSrc",
                ":nested-cli-nested:buildSrc:buildSrc",
                ":nested-cli",
                ":nested-cli:buildSrc",
                ":nested-cli:buildSrc:buildSrc",
                ":buildSrc",
                ":buildSrc:buildSrc",
        ]

        def configureBuildBuildOperations = buildOperations.all(ConfigureBuildBuildOperationType)
        configureBuildBuildOperations*.details.buildPath == configureOrder
        def loadProjectsBuildOperations = buildOperations.all(LoadProjectsBuildOperationType)
        loadProjectsBuildOperations*.details.buildPath == configureOrder

        def dirs = configureOrder
            .collect { it.substring(1) } // strip leading :
            .collect { it.replaceAll(":", "/") }
            .collect { it.replaceAll("nested-nested", "nested/nested-nested") }
            .collect { it.replaceAll("nested-cli-nested", "nested-cli/nested-cli-nested") }
            .collect { it ? file(it) : testDirectory }
            .collect { it.absolutePath }

        loadProjectsBuildOperations*.result.rootProject.projectDir == dirs
    }

}
