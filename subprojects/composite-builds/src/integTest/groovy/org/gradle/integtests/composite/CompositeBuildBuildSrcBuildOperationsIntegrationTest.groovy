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

package org.gradle.integtests.composite

import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.initialization.buildsrc.BuildBuildSrcBuildOperationType
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.operations.lifecycle.RunRequestedWorkBuildOperationType

import static org.gradle.integtests.fixtures.TestableBuildOperationRecord.buildOp

class CompositeBuildBuildSrcBuildOperationsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            file("buildSrc/src/main/java/Thing.java") << "class Thing { }"
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
    }

    def "generates configure, task graph and run tasks operations for buildSrc of included builds with #display"() {
        given:
        dependency 'org.test:buildB:1.0'
        buildB.file("buildSrc/settings.gradle") << """
            ${settings}
        """

        when:
        execute(buildA, ":jar", [])

        then:
        executed ":buildB:jar"

        and:
        def root = operations.root(RunBuildBuildOperationType)

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        def buildSrcOps = operations.all(BuildBuildSrcBuildOperationType)
        configureOps == [
            buildOp(displayName: "Configure build", parent: root, details: ["buildPath": ":"]),
            buildOp(displayName: "Configure build (:buildB)", parent: configureOps[0], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Configure build (:buildB:buildSrc)", parent: buildSrcOps[0], details: ["buildPath": ":buildB:buildSrc"])
        ]
        buildSrcOps == [
            buildOp(displayName: "Build buildSrc", parent: configureOps[1], details: ["buildPath": ":buildB"])
        ]

        def loadOps = operations.all(LoadBuildBuildOperationType)
        loadOps == [
            buildOp(displayName: "Load build", parent: root, details: ["buildPath": ":"]),
            buildOp(displayName: "Load build (:buildB)", parent: loadOps[0], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Load build (:buildB:buildSrc)", parent: buildSrcOps[0], details: ["buildPath": ":buildB:buildSrc"])
        ]

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath == [':', ':buildB', ':buildB:buildSrc']

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        def expectedTreeTaskGraphOps = [
            buildOp(displayName: "Calculate build tree task graph", parent: buildSrcOps[0]),
            buildOp(displayName: "Calculate build tree task graph", parent: root)
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeTaskGraphOps << buildOp(displayName: "Calculate build tree task graph", parent: operations.only("Load configuration cache state"))
        }
        treeTaskGraphOps == expectedTreeTaskGraphOps

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphOps = [
            buildOp(displayName: "Calculate task graph (:buildB:buildSrc)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB:buildSrc"]),
            buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[1], details: ["buildPath": ":"]),
            buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildB"])
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphOps += [
                buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[2], details: ["buildPath": ":"]),
                buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[2], details: ["buildPath": ":buildB"])
            ]
        }
        taskGraphOps == expectedTaskGraphOps

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = operations.matchingRegex("Run tasks.*")
        runTasksOps.size() == 3
        runTasksOps[0] == buildOp(displayName: "Run tasks (:buildB:buildSrc)", parent: buildSrcOps[0])
        // Build operations are run in parallel, so can appear in either order
        runTasksOps.takeRight(2).sort { it.displayName } == [
            buildOp(displayName: "Run tasks", parent: runMainTasks),
            buildOp(displayName: "Run tasks (:buildB)", parent: runMainTasks)
        ]

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB:buildSrc)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB:buildSrc"]),
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Notify task graph whenReady listeners", parent: treeTaskGraphOps[1], details: ["buildPath": ":"])
        ]

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    def "generates configure, task graph and run tasks operations when all builds have buildSrc with #display"() {
        given:
        dependency 'org.test:buildB:1.0'
        buildB.file("buildSrc/settings.gradle") << """
            ${settings}
        """

        buildA.file("buildSrc/src/main/java/Thing.java") << "class Thing { }"

        when:
        execute(buildA, ":jar", [])

        then:
        executed ":buildB:jar"

        and:
        def root = operations.root(RunBuildBuildOperationType)

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        def buildSrcOps = operations.all(BuildBuildSrcBuildOperationType)
        configureOps == [
            buildOp(displayName: "Configure build", parent: root, details: ["buildPath": ":"]),
            buildOp(displayName: "Configure build (:buildB)", parent: configureOps[0], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Configure build (:buildB:buildSrc)", parent: buildSrcOps[0], details: ["buildPath": ":buildB:buildSrc"]),
            buildOp(displayName: "Configure build (:buildSrc)", parent: buildSrcOps[1], details: ["buildPath": ":buildSrc"]),
        ]
        buildSrcOps == [
            buildOp(displayName: "Build buildSrc", parent: configureOps[1], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Build buildSrc", parent: configureOps[0], details: ["buildPath": ":"])
        ]

        def loadOps = operations.all(LoadBuildBuildOperationType)
        loadOps == [
            buildOp(displayName: "Load build", parent: root, details: ["buildPath": ":"]),
            buildOp(displayName: "Load build (:buildB)", parent: loadOps[0], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Load build (:buildB:buildSrc)", parent: buildSrcOps[0], details: ["buildPath": ":buildB:buildSrc"]),
            buildOp(displayName: "Load build (:buildSrc)", parent: buildSrcOps[1], details: ["buildPath": ":buildSrc"])
        ]

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath == [':', ':buildB', ':buildB:buildSrc', ':buildSrc']

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        def expectedTreeTaskGraphOps = [
            buildOp(displayName: "Calculate build tree task graph", parent: buildSrcOps[0]),
            buildOp(displayName: "Calculate build tree task graph", parent: buildSrcOps[1]),
            buildOp(displayName: "Calculate build tree task graph", parent: root),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeTaskGraphOps << buildOp(displayName: "Calculate build tree task graph", parent: operations.only("Load configuration cache state"))
        }
        treeTaskGraphOps == expectedTreeTaskGraphOps

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphOps = [
            buildOp(displayName: "Calculate task graph (:buildB:buildSrc)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB:buildSrc"]),
            buildOp(displayName: "Calculate task graph (:buildSrc)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildSrc"]),
            buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[2], details: ["buildPath": ":"]),
            buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[2], details: ["buildPath": ":buildB"]),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphOps += [
                buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[3], details: ["buildPath": ":"]),
                buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[3], details: ["buildPath": ":buildB"])
            ]
        }
        taskGraphOps == expectedTaskGraphOps

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = operations.matchingRegex("Run tasks.*")
        runTasksOps.size() == 4
        runTasksOps.take(2) == [
            buildOp(displayName: "Run tasks (:buildB:buildSrc)", parent: buildSrcOps[0]),
            buildOp(displayName: "Run tasks (:buildSrc)", parent: buildSrcOps[1])
        ]
        // Build operations are run in parallel, so can appear in either order
        runTasksOps.takeRight(2).sort { it.displayName } == [
            buildOp(displayName: "Run tasks", parent: runMainTasks),
            buildOp(displayName: "Run tasks (:buildB)", parent: runMainTasks)
        ]

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB:buildSrc)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB:buildSrc"]),
            buildOp(displayName: "Notify task graph whenReady listeners (:buildSrc)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildSrc"]),
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB)", parent: treeTaskGraphOps[2], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Notify task graph whenReady listeners", parent: treeTaskGraphOps[2], details: ["buildPath": ":"])
        ]

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }
}
