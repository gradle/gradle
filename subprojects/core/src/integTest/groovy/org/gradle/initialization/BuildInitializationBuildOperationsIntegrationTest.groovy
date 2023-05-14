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
import spock.lang.Issue

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

        def loadBuildBuildOperation = buildOperations.only(LoadBuildBuildOperationType)
        def evaluateSettingsBuildOperation = buildOperations.only(EvaluateSettingsBuildOperationType)
        def configureBuildBuildOperations = buildOperations.only(ConfigureBuildBuildOperationType)
        def loadProjectsBuildOperation = buildOperations.only(LoadProjectsBuildOperationType)
        def buildIdentifiedEvents = buildOperations.progress(BuildIdentifiedProgressDetails)
        def projectsIdentifiedEvents = buildOperations.progress(ProjectsIdentifiedProgressDetails)

        then:
        loadBuildBuildOperation.details.buildPath == ":"
        loadBuildBuildOperation.result.isEmpty()

        buildIdentifiedEvents.size() == 1
        buildIdentifiedEvents[0].details.buildPath == ':'

        evaluateSettingsBuildOperation.details.buildPath == ":"
        evaluateSettingsBuildOperation.result.isEmpty()
        assert loadBuildBuildOperation.id == evaluateSettingsBuildOperation.parentId

        configureBuildBuildOperations.details.buildPath == ":"
        configureBuildBuildOperations.result.isEmpty()

        loadProjectsBuildOperation.details.buildPath == ":"
        loadProjectsBuildOperation.result.rootProject.projectDir == settingsFile.parent
        buildOperations.first('Configure build').id == loadProjectsBuildOperation.parentId

        projectsIdentifiedEvents.size() == 1
        projectsIdentifiedEvents[0].details.rootProject.projectDir == settingsFile.parent
    }

    def "build operations are fired when settings script execution fails"() {
        settingsFile << """
            includeBuild("ignored")
            throw new RuntimeException("broken")
        """
        when:
        fails('foo')

        def loadBuildBuildOperation = buildOperations.only(LoadBuildBuildOperationType)
        def evaluateSettingsBuildOperation = buildOperations.only(EvaluateSettingsBuildOperationType)
        buildOperations.none(ConfigureBuildBuildOperationType)
        buildOperations.none(LoadProjectsBuildOperationType)
        def buildIdentifiedEvents = buildOperations.progress(BuildIdentifiedProgressDetails)
        def projectsIdentifiedEvents = buildOperations.progress(ProjectsIdentifiedProgressDetails)

        then:
        loadBuildBuildOperation.details.buildPath == ":"
        loadBuildBuildOperation.result == null

        buildIdentifiedEvents.size() == 1
        buildIdentifiedEvents[0].details.buildPath == ':'

        evaluateSettingsBuildOperation.details.buildPath == ":"
        evaluateSettingsBuildOperation.result == null
        assert loadBuildBuildOperation.id == evaluateSettingsBuildOperation.parentId

        projectsIdentifiedEvents.empty
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

        def loadOrder = [
            ":",
            ":nested",
            ":nested:nested-nested",
            ":nested-cli",
            ":nested-cli:nested-cli-nested",
            ":nested:nested-nested:buildSrc",
            ":nested:nested-nested:buildSrc:buildSrc",
            ":nested:buildSrc",
            ":nested:buildSrc:buildSrc",
            ":nested-cli:nested-cli-nested:buildSrc",
            ":nested-cli:nested-cli-nested:buildSrc:buildSrc",
            ":nested-cli:buildSrc",
            ":nested-cli:buildSrc:buildSrc",
            ":buildSrc",
            ":buildSrc:buildSrc",
        ]
        loadBuildBuildOperations*.details.buildPath == loadOrder
        loadBuildBuildOperations*.details.includedBy == [
            null,
            ":",
            ":nested",
            ":",
            ":nested-cli",
            ":nested:nested-nested",
            ":nested:nested-nested:buildSrc",
            ":nested",
            ":nested:buildSrc",
            ":nested-cli:nested-cli-nested",
            ":nested-cli:nested-cli-nested:buildSrc",
            ":nested-cli",
            ":nested-cli:buildSrc",
            ":",
            ":buildSrc",
        ]

        def buildIdentifiedEvents = buildOperations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath ==~ [
            ":",
            ":nested",
            ":nested-cli",
            ":nested:nested-nested",
            ":nested-cli:nested-cli-nested",
            ":nested:nested-nested:buildSrc",
            ":nested:nested-nested:buildSrc:buildSrc",
            ":nested:buildSrc",
            ":nested:buildSrc:buildSrc",
            ":nested-cli:nested-cli-nested:buildSrc",
            ":nested-cli:nested-cli-nested:buildSrc:buildSrc",
            ":nested-cli:buildSrc",
            ":nested-cli:buildSrc:buildSrc",
            ":buildSrc",
            ":buildSrc:buildSrc",
        ]

        def evaluateSettingsBuildOperations = buildOperations.all(EvaluateSettingsBuildOperationType)
        evaluateSettingsBuildOperations*.details.buildPath == loadOrder

        def configureOrder = [
                ":",
                ":nested:nested-nested",
                ":nested:nested-nested:buildSrc",
                ":nested:nested-nested:buildSrc:buildSrc",
                ":nested",
                ":nested:buildSrc",
                ":nested:buildSrc:buildSrc",
                ":nested-cli:nested-cli-nested",
                ":nested-cli:nested-cli-nested:buildSrc",
                ":nested-cli:nested-cli-nested:buildSrc:buildSrc",
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
        def projectsIdentifiedEvents = buildOperations.progress(ProjectsIdentifiedProgressDetails)
        projectsIdentifiedEvents*.details.buildPath == configureOrder

        def dirs = configureOrder
            .collect { it.substring(1) } // strip leading :
            .collect { it.replaceAll(":", "/") }
            .collect { it ? file(it) : testDirectory }
            .collect { it.absolutePath }

        loadProjectsBuildOperations*.result.rootProject.projectDir ==~ dirs
    }

    def "operations are fired when child build is not used"() {
        settingsFile << """
            rootProject.name = "root-changed"
            includeBuild('unused') {
                dependencySubstitution {
                    substitute(module("none:none:infinity")).using(project(":"))
                }
            }
        """
        file("unused/settings.gradle").createFile()

        when:
        succeeds()

        then:
        def loadBuildBuildOperations = buildOperations.all(LoadBuildBuildOperationType)
        def loadOrder = [
            ":",
            ":unused"
        ]
        loadBuildBuildOperations*.details.buildPath == loadOrder

        def buildIdentifiedEvents = buildOperations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath == loadOrder

        def evaluateSettingsBuildOperations = buildOperations.all(EvaluateSettingsBuildOperationType)
        evaluateSettingsBuildOperations*.details.buildPath == loadOrder

        def configureOrder = [
                ":"
        ]

        def configureBuildBuildOperations = buildOperations.all(ConfigureBuildBuildOperationType)
        configureBuildBuildOperations*.details.buildPath == configureOrder
        def loadProjectsBuildOperations = buildOperations.all(LoadProjectsBuildOperationType)
        loadProjectsBuildOperations*.details.buildPath == configureOrder
        def projectsIdentifiedEvents = buildOperations.progress(ProjectsIdentifiedProgressDetails)
        projectsIdentifiedEvents*.details.buildPath == configureOrder
    }

    @Issue("https://github.com/gradle/gradle/pull/18484")
    def "can run a build with build operation trace and --debug"() {
        buildFile << """
            task foo {
                doLast {
                    println 'foo'
                }
            }
        """
        executer.withStackTraceChecksDisabled()
        expect:
        succeeds('foo', "--debug")
    }
}
