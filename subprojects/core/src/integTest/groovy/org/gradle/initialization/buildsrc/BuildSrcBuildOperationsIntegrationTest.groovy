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

package org.gradle.initialization.buildsrc

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType

import java.util.regex.Pattern

class BuildSrcBuildOperationsIntegrationTest extends AbstractIntegrationSpec {
    BuildOperationsFixture ops
    def setup() {
        ops = new BuildOperationsFixture(executer, temporaryFolder)
        file("buildSrc/src/main/java/Thing.java") << "class Thing { }"
    }

    def "includes build identifier in build operations with #display"() {
        when:
        file("buildSrc/settings.gradle") << settings << "\n"
        succeeds()

        then:
        def root = ops.root(RunBuildBuildOperationType)

        def buildSrcOps = ops.all(BuildBuildSrcBuildOperationType)
        buildSrcOps.size() == 1
        buildSrcOps[0].displayName == "Build buildSrc"
        buildSrcOps[0].details.buildPath == ':'

        def loadOps = ops.all(LoadBuildBuildOperationType)
        loadOps.size() == 2
        loadOps[0].displayName == "Load build"
        loadOps[0].details.buildPath == ':'
        loadOps[0].parentId == root.id
        loadOps[1].displayName == "Load build (:buildSrc)"
        loadOps[1].details.buildPath == ':buildSrc'
        loadOps[1].parentId == buildSrcOps[0].id

        def buildIdentifiedEvents = ops.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 2
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ':buildSrc'

        def configureOps = ops.all(ConfigureBuildBuildOperationType)
        configureOps.size() == 2
        configureOps[0].displayName == "Configure build"
        configureOps[0].details.buildPath == ":"
        configureOps[0].parentId == root.id
        configureOps[1].displayName == "Configure build (:buildSrc)"
        configureOps[1].details.buildPath == ":buildSrc"
        configureOps[1].parentId == buildSrcOps[0].id

        def treeTaskGraphOps = ops.all(CalculateTreeTaskGraphBuildOperationType)
        treeTaskGraphOps.size() == 2
        treeTaskGraphOps[0].displayName == "Calculate build tree task graph"
        treeTaskGraphOps[0].parentId == buildSrcOps[0].id
        treeTaskGraphOps[1].displayName == "Calculate build tree task graph"
        treeTaskGraphOps[1].parentId == root.id

        def taskGraphOps = ops.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps.size() == 2
        taskGraphOps[0].displayName == "Calculate task graph (:buildSrc)"
        taskGraphOps[0].details.buildPath == ':buildSrc'
        taskGraphOps[0].parentId == treeTaskGraphOps[0].id
        taskGraphOps[1].displayName == "Calculate task graph"
        taskGraphOps[1].details.buildPath == ':'
        taskGraphOps[1].parentId == treeTaskGraphOps[1].id

        def runMainTasks = ops.first(Pattern.compile("Run main tasks"))
        runMainTasks.parentId == root.id

        def runTasksOps = ops.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 2
        runTasksOps[0].displayName == "Run tasks (:buildSrc)"
        runTasksOps[0].parentId == buildSrcOps[0].id
        runTasksOps[1].displayName == "Run tasks"
        runTasksOps[1].parentId == runMainTasks.id

        def graphNotifyOps = ops.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps.size() == 2
        graphNotifyOps[0].displayName == 'Notify task graph whenReady listeners (:buildSrc)'
        graphNotifyOps[0].details.buildPath == ':buildSrc'
        graphNotifyOps[0].parentId == treeTaskGraphOps[0].id
        graphNotifyOps[1].displayName == "Notify task graph whenReady listeners"
        graphNotifyOps[1].details.buildPath == ":"
        graphNotifyOps[1].parentId == treeTaskGraphOps[1].id

        def taskOps = ops.all(ExecuteTaskBuildOperationType)
        taskOps.size() > 1
        taskOps[0].details.buildPath == ':buildSrc'
        taskOps[0].parentId == runTasksOps[0].id
        taskOps.last().details.buildPath == ':'
        taskOps.last().parentId == runTasksOps[1].id

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "does not resolve configurations when configuring buildSrc build"() {
        when:
        succeeds()

        then:
        def buildSrcConfigure = ops.first("Configure build (:buildSrc)")
        ops.children(buildSrcConfigure, ResolveConfigurationDependenciesBuildOperationType).empty
    }
}
