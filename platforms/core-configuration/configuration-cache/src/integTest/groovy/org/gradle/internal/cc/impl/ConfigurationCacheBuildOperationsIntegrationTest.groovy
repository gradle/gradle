/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.EvaluateSettingsBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.initialization.ProjectsIdentifiedProgressDetails
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.operations.configuration.ConfigurationCacheCheckFingerprintBuildOperationType
import org.gradle.internal.configurationcache.ConfigurationCacheLoadBuildOperationType
import org.gradle.internal.configurationcache.ConfigurationCacheStoreBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion

class ConfigurationCacheBuildOperationsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits no load/store build operations when configuration cache is not used"() {
        given:
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        run 'assemble'

        then:
        operations.none(ConfigurationCacheCheckFingerprintBuildOperationType)
        operations.none(ConfigurationCacheLoadBuildOperationType)
        operations.none(ConfigurationCacheStoreBuildOperationType)
        compositeBuildWorkGraphCalculated()
        hasCompositeBuildsWorkGraphPopulated()
    }

    def "emits relevant build operations when configuration cache is used"() {
        given:
        withLibBuild()

        when:
        inDirectory 'lib'
        configurationCacheRun 'assemble'

        then:
        workGraphStoredAndLoaded()
        def checkOp = operations.only(ConfigurationCacheCheckFingerprintBuildOperationType)
        def loadOp = operations.only(ConfigurationCacheLoadBuildOperationType)
        def storeOp = operations.only(ConfigurationCacheStoreBuildOperationType)
        with(checkOp.result) {
            status == "NOT_FOUND"
            buildInvalidationReasons == []
            projectInvalidationReasons == []
        }
        with(storeOp.result) {
            cacheEntrySize > 0
        }
        with(loadOp.result) {
            cacheEntrySize == storeOp.result.cacheEntrySize
        }
        def buildInvocationId = loadOp.result.originBuildInvocationId

        when:
        inDirectory 'lib'
        configurationCacheRun 'assemble'

        then:
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "VALID"
            buildInvalidationReasons == []
            projectInvalidationReasons == []
        }

        def loadOpInCcHitBuild = operations.only(ConfigurationCacheLoadBuildOperationType)
        workGraphLoaded()
        loadOpInCcHitBuild.result.originBuildInvocationId == buildInvocationId
    }

    def "emits invalidation reason in the build operation"() {
        given:
        withLibBuild()
        file('lib/settings.gradle') << """
            println System.getProperty("settings.property", "empty")
        """
        file('lib/build.gradle') << """
            println System.getProperty("project.property", "empty")
        """

        when:
        inDirectory 'lib'
        configurationCacheRun 'assemble'

        then:
        workGraphStoredAndLoaded()
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "NOT_FOUND"
            buildInvalidationReasons == []
            projectInvalidationReasons == []
        }

        when: "changing the build-level contents"

        inDirectory 'lib'
        configurationCacheRun 'assemble', '-Dsettings.property=changed'

        then: "build fingerprint is invalidated"
        workGraphStoredAndLoaded()
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "INVALID"
            buildInvalidationReasons == [
                [
                    buildPath: ":",
                    invalidationReasons: [
                        [message: "system property 'settings.property' has changed"]
                    ]
                ]
            ]

            projectInvalidationReasons == []
        }

        when: "changing the project-level contents"
        inDirectory 'lib'
        configurationCacheRun 'assemble', '-Dsettings.property=changed', '-Dproject.property=changed'

        then: "build fingerprint is invalidated"
        workGraphStoredAndLoaded()
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "INVALID"
            buildInvalidationReasons == [
                [
                    buildPath: ":",
                    invalidationReasons: [
                        [message: "system property 'project.property' has changed"]
                    ]
                ]
            ]

            projectInvalidationReasons == []
        }
    }

    def "emits relevant build operations when configuration cache is used - included build dependency"() {
        given:
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        compositeBuildWorkGraphStoredAndLoaded()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        compositeBuildWorkGraphLoaded()
    }

    def "emits relevant build operations when configuration cache is used - included build logic"() {
        given:
        withLibBuild(true)
        file('settings.gradle') << """
            pluginManagement {
                includeBuild 'lib'
            }
        """
        buildFile << """
            plugins {
                id 'my-plugin'
            }
        """
        when:
        configurationCacheRun 'help'

        then:
        outputContains('In script plugin')
        buildLogicBuiltAndWorkGraphStoredAndLoaded()

        // TODO - should get a cache hit for this build (https://github.com/gradle/gradle/issues/23267)
        when:
        configurationCacheRun 'help'

        then:
        buildLogicBuiltAndWorkGraphStoredAndLoaded()

        when:
        configurationCacheRun 'help'

        then:
        compositeBuildRootBuildWorkGraphLoaded()
    }

    def "emits relevant build operations when configuration cache is used - buildSrc"() {
        given:
        withBuildSrc()

        when:
        configurationCacheRun 'assemble'

        then:
        buildLogicBuiltAndWorkGraphStoredAndLoaded(':buildSrc', file('buildSrc'))

        when:
        configurationCacheRun 'assemble'

        then:
        compositeBuildRootBuildWorkGraphLoaded(':buildSrc', file('buildSrc'))
    }

    def "emits relevant build operations when configuration cache is used - with unused included build"() {
        given:
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
        configurationCacheRun 'help'

        then:
        hasOperationsForStoreAndLoad()
        hasCompositeWithUnusedBuildIdentified()
        operations.all(EvaluateSettingsBuildOperationType).size() == 2
        operations.only(ConfigureBuildBuildOperationType)
        operations.only(LoadProjectsBuildOperationType)
        operations.only(ConfigureProjectBuildOperationType)

        when:
        configurationCacheRun 'help'

        then:
        hasOperationsForLoad()
        hasCompositeWithUnusedBuildIdentified()
        operations.none(EvaluateSettingsBuildOperationType)
        operations.none(ConfigureBuildBuildOperationType)
        operations.none(LoadProjectsBuildOperationType)
        operations.none(ConfigureProjectBuildOperationType)
    }

    def "captures store failure in build operation"() {
        given:
        withLibBuild()
        file("lib/build.gradle") << """
            gradle.buildFinished { }
        """

        when:
        inDirectory 'lib'
        configurationCacheFails 'assemble'

        then:
        operations.none(ConfigurationCacheLoadBuildOperationType)
        def storeOp = operations.only(ConfigurationCacheStoreBuildOperationType)
        with(storeOp.result) {
            cacheEntrySize > 0
        }
        storeOp.failure == """org.gradle.internal.cc.impl.ConfigurationCacheProblemsException: Configuration cache problems found in this build.

1 problem was found storing the configuration cache.
- Build file 'build.gradle': line 6: registration of listener on 'Gradle.buildFinished' is unsupported
  See https://docs.gradle.org/${GradleVersion.current().version}/userguide/configuration_cache.html#config_cache:requirements:build_listeners
"""
    }

    void hasCompositeWithUnusedBuildIdentified() {
        def buildIdentified = operations.progress(BuildIdentifiedProgressDetails)
        assert buildIdentified.size() == 2
        with(buildIdentified[0].details) {
            assert buildPath == ':'
        }
        with(buildIdentified[1].details) {
            assert buildPath == ':unused'
        }
        def projectsIdentified = operations.progress(ProjectsIdentifiedProgressDetails)
        assert projectsIdentified.size() == 1
        with(projectsIdentified[0].details) {
            assert buildPath == ':'
        }
    }

    private void workGraphStoredAndLoaded() {
        hasOperationsForStoreAndLoad()
        buildIdentified()

        def loadBuildOp = operations.only(LoadBuildBuildOperationType)

        def evaluateSettingsOp = operations.only(EvaluateSettingsBuildOperationType)
        assert evaluateSettingsOp.parentId == loadBuildOp.id

        def configureBuildOp = operations.only(ConfigureBuildBuildOperationType)

        def loadProjectsOp = operations.only(LoadProjectsBuildOperationType)
        assert loadProjectsOp.parentId == configureBuildOp.id

        def configureProjectOp = operations.only(ConfigureProjectBuildOperationType)
        assert configureProjectOp.parentId == configureBuildOp.id

        hasWorkGraphsPopulated()
    }

    private void workGraphLoaded() {
        hasOperationsForLoad()
        buildIdentified()

        operations.none(LoadBuildBuildOperationType)
        operations.none(LoadProjectsBuildOperationType)
        operations.none(EvaluateSettingsBuildOperationType)
        operations.none(ConfigureBuildBuildOperationType)
        operations.none(ConfigureProjectBuildOperationType)

        hasWorkGraphPopulated()
    }

    private void compositeBuildWorkGraphStoredAndLoaded() {
        hasOperationsForStoreAndLoad()
        compositeBuildWorkGraphCalculated()
        hasCompositeBuildsWorkGraphsPopulated()
    }

    private void compositeBuildWorkGraphCalculated() {
        compositeBuildsIdentified(testDirectory.file('app'))

        def loadBuildOps = operations.all(LoadBuildBuildOperationType)
        assert loadBuildOps.size() == 2
        def loadRootBuild = loadBuildOps[0]
        with(loadRootBuild) {
            assert details.buildPath == ':'
        }
        def loadChildBuild = loadBuildOps[1]
        with(loadChildBuild) {
            assert details.buildPath == ':lib'
            assert parentId == loadRootBuild.id
        }

        def evaluateSettingsOps = operations.all(EvaluateSettingsBuildOperationType)
        assert evaluateSettingsOps.size() == 2
        with(evaluateSettingsOps[0]) {
            assert details.buildPath == ':'
            assert parentId == loadRootBuild.id
        }
        with(evaluateSettingsOps[1]) {
            assert details.buildPath == ':lib'
            assert parentId == loadChildBuild.id
        }

        def configureBuildOps = operations.all(ConfigureBuildBuildOperationType)
        assert configureBuildOps.size() == 2
        def configureRootBuild = configureBuildOps[0]
        with(configureRootBuild) {
            assert details.buildPath == ':'
        }
        def configureChildBuild = configureBuildOps[1]
        with(configureChildBuild) {
            assert details.buildPath == ':lib'
            assert parentId == configureRootBuild.id
        }

        def loadProjectsOps = operations.all(LoadProjectsBuildOperationType)
        assert loadProjectsOps.size() == 2
        with(loadProjectsOps[0]) {
            assert details.buildPath == ':'
            assert parentId == configureRootBuild.id
        }
        with(loadProjectsOps[1]) {
            assert details.buildPath == ':lib'
            assert parentId == configureChildBuild.id
        }

        def configureProjectOps = operations.all(ConfigureProjectBuildOperationType)
        assert configureProjectOps.size() == 2
        with(configureProjectOps[0]) {
            assert details.buildPath == ':lib'
            assert details.projectPath == ':'
            assert parentId == configureChildBuild.id
        }
        with(configureProjectOps[1]) {
            assert details.buildPath == ':'
            assert details.projectPath == ':'
            assert parentId == configureRootBuild.id
        }
    }

    private void buildLogicBuiltAndWorkGraphCalculated(String buildLogicBuild = ':lib') {
        def loadBuildOps = operations.all(LoadBuildBuildOperationType)
        assert loadBuildOps.size() == 2
        def loadRootBuild = loadBuildOps[0]
        with(loadRootBuild) {
            assert details.buildPath == ':'
        }
        def loadBuildLogicBuild = loadBuildOps[1]
        with(loadBuildLogicBuild) {
            assert details.buildPath == buildLogicBuild
            // parent is different for buildSrc and included build
        }

        def evaluateSettingsOps = operations.all(EvaluateSettingsBuildOperationType)
        assert evaluateSettingsOps.size() == 2
        with(evaluateSettingsOps[0]) {
            assert details.buildPath == ':'
            assert parentId == loadRootBuild.id
        }
        with(evaluateSettingsOps[1]) {
            assert details.buildPath == buildLogicBuild
            assert parentId == loadBuildLogicBuild.id
        }

        def configureBuildOps = operations.all(ConfigureBuildBuildOperationType)
        assert configureBuildOps.size() == 2
        def configureRootBuild = configureBuildOps[0]
        with(configureRootBuild) {
            assert details.buildPath == ':'
        }
        def configureBuildLogicBuild = configureBuildOps[1]
        with(configureBuildLogicBuild) {
            assert details.buildPath == buildLogicBuild
        }

        def loadProjectsOps = operations.all(LoadProjectsBuildOperationType)
        assert loadProjectsOps.size() == 2
        with(loadProjectsOps[0]) {
            assert details.buildPath == ':'
            assert parentId == configureRootBuild.id
        }
        with(loadProjectsOps[1]) {
            assert details.buildPath == buildLogicBuild
            assert parentId == configureBuildLogicBuild.id
        }

        def configureProjectOps = operations.all(ConfigureProjectBuildOperationType)
        assert configureProjectOps.size() == 2
        with(configureProjectOps.find { it.details.buildPath == ':' }) {
            assert details.projectPath == ':'
            assert parentId == configureRootBuild.id
        }
        with(configureProjectOps.find { it.details.buildPath == buildLogicBuild }) {
            assert details.projectPath == ':'
            assert parentId == configureBuildLogicBuild.id
        }

        def workGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        assert workGraphOps.size() == 3

        def calculateWorkOps = operations.all(CalculateTaskGraphBuildOperationType)
        assert calculateWorkOps.size() == 3
        with(calculateWorkOps[0]) {
            assert details.buildPath == buildLogicBuild
            assert parentId == workGraphOps[0].id
        }
        with(calculateWorkOps[1]) {
            assert details.buildPath == ':'
            assert parentId == workGraphOps[1].id
        }
        with(calculateWorkOps[2]) {
            assert details.buildPath == ':'
            assert parentId == workGraphOps[2].id
        }
        assert operations.all(NotifyTaskGraphWhenReadyBuildOperationType).size() == 2
    }

    private void compositeBuildWorkGraphLoaded() {
        hasOperationsForLoad()
        compositeBuildsIdentified(testDirectory.file('app'))

        operations.none(LoadBuildBuildOperationType)
        operations.none(LoadProjectsBuildOperationType)
        operations.none(EvaluateSettingsBuildOperationType)
        operations.none(ConfigureBuildBuildOperationType)
        operations.none(ConfigureProjectBuildOperationType)

        hasCompositeBuildsWorkGraphPopulated()
    }

    private void buildLogicBuiltAndWorkGraphStoredAndLoaded(String buildLogicBuild = ':lib', TestFile buildLogicDir = testDirectory.file('lib')) {
        hasOperationsForStoreAndLoad()
        compositeBuildsIdentified(testDirectory, buildLogicBuild, buildLogicDir)
        buildLogicBuiltAndWorkGraphCalculated(buildLogicBuild)
    }

    private void compositeBuildRootBuildWorkGraphLoaded(String childBuild = ':lib', TestFile childBuildDir = testDirectory.file('lib')) {
        hasOperationsForLoad()
        compositeBuildsIdentified(testDirectory, childBuild, childBuildDir)

        operations.none(LoadBuildBuildOperationType)
        operations.none(LoadProjectsBuildOperationType)
        operations.none(EvaluateSettingsBuildOperationType)
        operations.none(ConfigureBuildBuildOperationType)
        operations.none(ConfigureProjectBuildOperationType)

        operations.only(CalculateTreeTaskGraphBuildOperationType)
        operations.only(CalculateTaskGraphBuildOperationType)
        operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
    }

    private void buildIdentified() {
        def buildIdentified = operations.progress(BuildIdentifiedProgressDetails)
        assert buildIdentified.size() == 1
        with(buildIdentified[0].details) {
            assert buildPath == ':'
        }

        def projectsIdentified = operations.progress(ProjectsIdentifiedProgressDetails)
        assert projectsIdentified.size() == 1
        with(projectsIdentified[0].details) {
            assert buildPath == ':'
        }
    }

    private void compositeBuildsIdentified(TestFile rootDir = testDirectory, String childBuild = ':lib', TestFile childDir = testDirectory.file('lib')) {
        def buildIdentified = operations.progress(BuildIdentifiedProgressDetails)
        assert buildIdentified.size() == 2
        with(buildIdentified[0].details) {
            assert buildPath == ':'
        }
        with(buildIdentified[1].details) {
            assert buildPath == childBuild
        }
        def projectsIdentified = operations.progress(ProjectsIdentifiedProgressDetails)
        assert projectsIdentified.size() == 2
        with(projectsIdentified[0].details) {
            assert buildPath == ':'
            assert rootProject.projectDir == rootDir.absolutePath
            assert rootProject.buildFile == rootDir.file("build.gradle").absolutePath
        }
        with(projectsIdentified[1].details) {
            assert buildPath == childBuild
            assert rootProject.projectDir == childDir.absolutePath
            assert rootProject.buildFile == childDir.file("build.gradle").absolutePath
        }
    }

    private void hasWorkGraphPopulated() {
        def calculateTreeGraphOp = operations.only(CalculateTreeTaskGraphBuildOperationType)

        def calculateGraphOp = operations.only(CalculateTaskGraphBuildOperationType)
        assert calculateGraphOp.parentId == calculateTreeGraphOp.id

        def notifyGraphReadyOp = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        assert notifyGraphReadyOp.parentId == calculateTreeGraphOp.id
    }

    private void hasWorkGraphsPopulated() {
        def calculateTreeGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        assert calculateTreeGraphOps.size() == 2

        def calculateGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        assert calculateGraphOps.size() == 2

        def calculateTreeGraphOp = calculateTreeGraphOps[0]

        def calculateGraphOp = calculateGraphOps[0]
        assert calculateGraphOp.parentId == calculateTreeGraphOp.id

        def notifyGraphReadyOp = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        assert notifyGraphReadyOp.parentId == calculateTreeGraphOp.id

        def calculateTreeGraphOp2 = calculateTreeGraphOps[1]

        def calculateGraphOp2 = calculateGraphOps[1]
        assert calculateGraphOp2.parentId == calculateTreeGraphOp2.id

        assert calculateGraphOp.result.requestedTaskPaths == calculateGraphOp2.result.requestedTaskPaths
    }

    private void hasCompositeBuildsWorkGraphPopulated() {
        def workGraphOp = operations.only(CalculateTreeTaskGraphBuildOperationType)
        def buildWorkGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        assert buildWorkGraphOps.size() == 2
        with(buildWorkGraphOps[0]) {
            assert details.buildPath == ':'
            assert parentId == workGraphOp.id
        }
        with(buildWorkGraphOps[1]) {
            assert details.buildPath == ':lib'
            assert parentId == workGraphOp.id
        }

        def notificationOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        assert notificationOps.size() == 2
        with(notificationOps[0]) {
            assert details.buildPath == ':lib'
            assert parentId == workGraphOp.id
        }
        with(notificationOps[1]) {
            assert details.buildPath == ':'
            assert parentId == workGraphOp.id
        }
    }

    private void hasCompositeBuildsWorkGraphsPopulated() {
        def treeWorkGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        assert treeWorkGraphOps.size() == 2

        def workGraphOp = treeWorkGraphOps[0]
        def loadWorkGraphOp = treeWorkGraphOps[1]
        def buildWorkGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        assert buildWorkGraphOps.size() == 4
        with(buildWorkGraphOps[0]) {
            assert details.buildPath == ':'
            assert parentId == workGraphOp.id
        }
        with(buildWorkGraphOps[1]) {
            assert details.buildPath == ':lib'
            assert parentId == workGraphOp.id
        }
        with(buildWorkGraphOps[2]) {
            assert details.buildPath == ':'
            assert parentId == loadWorkGraphOp.id
        }
        with(buildWorkGraphOps[3]) {
            assert details.buildPath == ':lib'
            assert parentId == loadWorkGraphOp.id
        }

        def notificationOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        assert notificationOps.size() == 2
        with(notificationOps[0]) {
            assert details.buildPath == ':lib'
            assert parentId == workGraphOp.id
        }
        with(notificationOps[1]) {
            assert details.buildPath == ':'
            assert parentId == workGraphOp.id
        }
    }

    private void hasOperationsForLoad() {
        operations.only(ConfigurationCacheLoadBuildOperationType)
        operations.none(ConfigurationCacheStoreBuildOperationType)
    }

    private void hasOperationsForStoreAndLoad() {
        operations.only(ConfigurationCacheLoadBuildOperationType)
        operations.only(ConfigurationCacheStoreBuildOperationType)
    }

    void withBuildSrc() {
        createDir("buildSrc") {
            file("src/main/java/Util.java") << """
                public class Util { }
            """
        }
        buildFile << """
            plugins {
                id("lifecycle-base")
            }
            new Util()
        """
    }

    private TestFile withLibBuild(boolean withPrecompiledScriptPlugin = false) {
        createDir('lib') {
            file('settings.gradle') << """
                rootProject.name = 'lib'
            """
            if (withPrecompiledScriptPlugin) {
                file('build.gradle') << """
                    plugins { id 'groovy-gradle-plugin' }
                """
                file('src/main/groovy/my-plugin.gradle') << """
                    println 'In script plugin'
                """
            }
            file('build.gradle') << """
                plugins { id 'java' }
                group = 'org.test'
                version = '1.0'
            """

            file('src/main/java/Lib.java') << """
                public class Lib { public static void main() {
                    System.out.println("Before!");
                } }
            """
        }
    }

    private TestFile withAppBuild() {
        createDir('app') {
            file('settings.gradle') << """
                includeBuild '../lib'
            """
            file('build.gradle') << """
                plugins {
                    id 'java'
                    id 'application'
                }
                application {
                   mainClass = 'Main'
                }
                dependencies {
                    implementation 'org.test:lib:1.0'
                }
            """
            file('src/main/java/Main.java') << """
                class Main { public static void main(String[] args) {
                    Lib.main();
                } }
            """
        }
    }
}
