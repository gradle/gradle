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

package org.gradle.configurationcache

import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.EvaluateSettingsBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.initialization.ProjectsIdentifiedProgressDetails
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.configurationcache.ConfigurationCacheLoadBuildOperationType
import org.gradle.internal.configurationcache.ConfigurationCacheStoreBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.ToBeImplemented

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
        operations.none(ConfigurationCacheLoadBuildOperationType)
        operations.none(ConfigurationCacheStoreBuildOperationType)
        compositeBuildWorkGraphCalculated()
    }

    def "emits relevant build operations when configuration cache is used"() {
        given:
        withLibBuild()

        when:
        inDirectory 'lib'
        configurationCacheRun 'assemble'

        then:
        workGraphStored()

        when:
        inDirectory 'lib'
        configurationCacheRun 'assemble'

        then:
        workGraphLoaded()
    }

    def "emits relevant build operations when configuration cache is used - included build dependency"() {
        given:
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        hasOperationsForStore()
        compositeBuildWorkGraphCalculated()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        compositeBuildWorkGraphLoaded()
    }

    def "emits relevant build operations when configuration cache is used - included build dependency (load-after-store)"() {
        given:
        executer.beforeExecute {
            withArgument("-Dorg.gradle.configuration-cache.internal.load-after-store=true")
        }
        withLibBuild()
        withAppBuild()

        when:
        inDirectory 'app'
        configurationCacheRun 'assemble'

        then:
        with(operations.only(ConfigurationCacheStoreBuildOperationType)) {
            details == [:]
            it.result == [:]
        }
        operations.only(ConfigurationCacheLoadBuildOperationType)
        compositeBuildWorkGraphCalculatedAndLoaded()

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
        hasOperationsForStore()
        buildLogicBuiltAndWorkGraphCalculated()

        when:
        configurationCacheRun 'help'

        then:
        compositeBuildWorkGraphLoaded()
    }

    @ToBeImplemented("progress events are not fired for buildSrc")
    def "emits relevant build operations when configuration cache is used - buildSrc"() {
        given:
        withBuildSrc()

        when:
        configurationCacheRun 'assemble'

        then:
        hasOperationsForStore()
        buildLogicBuiltAndWorkGraphCalculated(':buildSrc')

        when:
        configurationCacheRun 'assemble'

        then:
        hasOperationsForLoad()

        operations.none(ConfigurationCacheStoreBuildOperationType)
        operations.progress(BuildIdentifiedProgressDetails).size() == 1 // Not yet fired for buildSrc
        operations.none(LoadBuildBuildOperationType)
        operations.none(LoadProjectsBuildOperationType)
        operations.progress(ProjectsIdentifiedProgressDetails).size() == 1 // Not yet fired for buildSrc
        operations.none(EvaluateSettingsBuildOperationType)
        operations.none(ConfigureBuildBuildOperationType)
        operations.none(ConfigureProjectBuildOperationType)
    }

    private void workGraphStored() {
        hasOperationsForStore()
        buildIdentified()

        def loadBuildOp = operations.only(LoadBuildBuildOperationType)

        def evaluateSettingsOp = operations.only(EvaluateSettingsBuildOperationType)
        assert evaluateSettingsOp.parentId == loadBuildOp.id

        def configureBuildOp = operations.only(ConfigureBuildBuildOperationType)

        def loadProjectsOp = operations.only(LoadProjectsBuildOperationType)
        assert loadProjectsOp.parentId == configureBuildOp.id

        def configureProjectOp = operations.only(ConfigureProjectBuildOperationType)
        assert configureProjectOp.parentId == configureBuildOp.id

        hasWorkGraphPopulated()
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

    private void compositeBuildWorkGraphCalculated() {
        compositeBuildsIdentified()

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

        hasCompositeBuildsWorkGraphPopulated()
    }

    private void buildLogicBuiltAndWorkGraphCalculated(String buildLogicBuild = ':lib') {
        compositeBuildsIdentified(buildLogicBuild)

        assert operations.all(LoadBuildBuildOperationType).size() == 2
        assert operations.all(LoadProjectsBuildOperationType).size() == 2
        assert operations.all(EvaluateSettingsBuildOperationType).size() == 2
        assert operations.all(ConfigureBuildBuildOperationType).size() == 2
        assert operations.all(ConfigureProjectBuildOperationType).size() == 2
        assert operations.all(CalculateTreeTaskGraphBuildOperationType).size() == 2
        assert operations.all(CalculateTaskGraphBuildOperationType).size() == 2
        assert operations.all(NotifyTaskGraphWhenReadyBuildOperationType).size() == 2
    }

    private void compositeBuildWorkGraphCalculatedAndLoaded() {
        compositeBuildsIdentified()

        assert operations.all(LoadBuildBuildOperationType).size() == 2
        assert operations.all(LoadProjectsBuildOperationType).size() == 2
        assert operations.all(EvaluateSettingsBuildOperationType).size() == 2
        assert operations.all(ConfigureBuildBuildOperationType).size() == 2
        assert operations.all(ConfigureProjectBuildOperationType).size() == 2
        assert operations.all(CalculateTreeTaskGraphBuildOperationType).size() == 2
        assert operations.all(CalculateTaskGraphBuildOperationType).size() == 4
        assert operations.all(NotifyTaskGraphWhenReadyBuildOperationType).size() == 2 // only fired when calculating
    }

    private void compositeBuildWorkGraphLoaded() {
        hasOperationsForLoad()
        compositeBuildsIdentified()

        operations.none(LoadBuildBuildOperationType)
        operations.none(LoadProjectsBuildOperationType)
        operations.none(EvaluateSettingsBuildOperationType)
        operations.none(ConfigureBuildBuildOperationType)
        operations.none(ConfigureProjectBuildOperationType)

        hasCompositeBuildsWorkGraphPopulated()
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

    private void compositeBuildsIdentified(String childBuild = ':lib') {
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
        }
        with(projectsIdentified[1].details) {
            assert buildPath == childBuild
        }
    }

    private void hasWorkGraphPopulated() {
        def calculateTreeGraphOp = operations.only(CalculateTreeTaskGraphBuildOperationType)

        def calculateGraphOp = operations.only(CalculateTaskGraphBuildOperationType)
        assert calculateGraphOp.parentId == calculateTreeGraphOp.id

        def notifyGraphReadyOp = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        assert notifyGraphReadyOp.parentId == calculateTreeGraphOp.id
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

    private void hasOperationsForLoad() {
        def loadOp = operations.only(ConfigurationCacheLoadBuildOperationType)
        with(loadOp) {
            assert details == [:]
            assert it.result == [:]
        }
        operations.none(ConfigurationCacheStoreBuildOperationType)
    }

    private void hasOperationsForStore() {
        operations.none(ConfigurationCacheLoadBuildOperationType)
        def storeOp = operations.only(ConfigurationCacheStoreBuildOperationType)
        with(storeOp) {
            details == [:]
            it.result == [:]
        }
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
