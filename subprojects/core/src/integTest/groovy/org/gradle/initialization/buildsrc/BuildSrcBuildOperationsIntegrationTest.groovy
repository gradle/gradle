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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.operations.lifecycle.RunRequestedWorkBuildOperationType

import static org.gradle.integtests.fixtures.TestableBuildOperationRecord.buildOp

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
        def configureOps = ops.all(ConfigureBuildBuildOperationType)
        configureOps == [
            buildOp(displayName: "Configure build", details: [buildPath: ':'], parent: root),
            buildOp(displayName: "Configure build (:buildSrc)", details: [buildPath: ':buildSrc'], parent: buildSrcOps[0])
        ]
        buildSrcOps == [
            buildOp(displayName: "Build buildSrc", details: [buildPath: ':'], parent: configureOps[0])
        ]

        def loadOps = ops.all(LoadBuildBuildOperationType)
        loadOps == [
            buildOp(displayName: "Load build", details: [buildPath: ':'], parent: root),
            buildOp(displayName: "Load build (:buildSrc)", details: [buildPath: ':buildSrc'], parent: buildSrcOps[0])
        ]

        def buildIdentifiedEvents = ops.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath == [':', ':buildSrc']

        def treeTaskGraphOps = ops.all(CalculateTreeTaskGraphBuildOperationType)
        def expectedTreeTaskGraphOps = [
            buildOp(displayName: "Calculate build tree task graph", parent: buildSrcOps[0]),
            buildOp(displayName: "Calculate build tree task graph", parent: root)
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeTaskGraphOps << buildOp(displayName: "Calculate build tree task graph", parent: ops.only("Load configuration cache state"))
        }
        treeTaskGraphOps == expectedTreeTaskGraphOps

        def taskGraphOps = ops.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphOps = [
            buildOp(displayName: "Calculate task graph (:buildSrc)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildSrc"]),
            buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[1], details: ["buildPath": ":"]),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphOps += [
                buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[2], details: ["buildPath": ":"]),
            ]
        }
        taskGraphOps == expectedTaskGraphOps

        def runMainTasks = ops.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = ops.matchingRegex("Run tasks.*")
        runTasksOps == [
            buildOp(displayName: "Run tasks (:buildSrc)", parent: buildSrcOps[0]),
            buildOp(displayName: "Run tasks", parent: runMainTasks)
        ]

        def graphNotifyOps = ops.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: 'Notify task graph whenReady listeners (:buildSrc)', details: [buildPath: ':buildSrc'], parent: treeTaskGraphOps[0]),
            buildOp(displayName: "Notify task graph whenReady listeners", details: [buildPath: ":"], parent: treeTaskGraphOps[1])
        ]

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
