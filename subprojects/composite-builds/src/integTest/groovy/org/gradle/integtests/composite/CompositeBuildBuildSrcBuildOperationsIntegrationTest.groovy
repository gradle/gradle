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
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.operations.lifecycle.RunRequestedWorkBuildOperationType

import java.util.regex.Pattern

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

        def buildSrcOps = operations.all(BuildBuildSrcBuildOperationType)
        buildSrcOps.size() == 1
        buildSrcOps[0].displayName == "Build buildSrc"
        buildSrcOps[0].details.buildPath == ":buildB"

        def loadOps = operations.all(LoadBuildBuildOperationType)
        verifyBuildPathOperations(
            "Load build",
            loadOps,
            [
                [":", root.id],
                [":buildB", loadOps[0].id],
                [":buildB:buildSrc", buildSrcOps[0].id]
            ]
        )

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 3
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ':buildB'
        buildIdentifiedEvents[2].details.buildPath == ':buildB:buildSrc'

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        verifyBuildPathOperations(
            "Configure build",
            configureOps,
            [
                [":", root.id],
                [":buildB", configureOps[0].id],
                [":buildB:buildSrc", buildSrcOps[0].id]
            ]
        )

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        verifyTaskGraphOps(
            operations: treeTaskGraphOps,
            expectedParents: [buildSrcOps[0].id, root.id],
            expectedBuildPaths: [
                [":buildB:buildSrc", treeTaskGraphOps[0].id],
                [":", treeTaskGraphOps[1].id],
                [":buildB", treeTaskGraphOps[1].id]
            ],
            extraBuildPathsWithCC: [":", ":buildB"]
        )

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 3
        runTasksOps[0].displayName == "Run tasks (:buildB:buildSrc)"
        runTasksOps[0].parentId == buildSrcOps[0].id
        // Build operations are run in parallel, so can appear in either order
        [runTasksOps[1].displayName, runTasksOps[2].displayName].sort() == ["Run tasks", "Run tasks (:buildB)"]
        runTasksOps[1].parentId == runMainTasks.id
        runTasksOps[2].parentId == runMainTasks.id

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyBuildPathOperations(
            "Notify task graph whenReady listeners",
            graphNotifyOps,
            [
                [":buildB:buildSrc", treeTaskGraphOps[0].id],
                [":buildB", treeTaskGraphOps[1].id],
                [":", treeTaskGraphOps[1].id]
            ]
        )

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

        def buildSrcOps = operations.all(BuildBuildSrcBuildOperationType)
        buildSrcOps.size() == 2
        buildSrcOps[0].displayName == "Build buildSrc"
        buildSrcOps[0].details.buildPath == ":buildB"
        buildSrcOps[1].displayName == "Build buildSrc"
        buildSrcOps[1].details.buildPath == ":"

        def loadOps = operations.all(LoadBuildBuildOperationType)
        verifyBuildPathOperations(
            "Load build",
            loadOps,
            [
                [":", root.id],
                [":buildB", loadOps[0].id],
                [":buildB:buildSrc", buildSrcOps[0].id],
                [":buildSrc", buildSrcOps[1].id]
            ]
        )

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 4
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ':buildB'
        buildIdentifiedEvents[2].details.buildPath == ':buildB:buildSrc'
        buildIdentifiedEvents[3].details.buildPath == ':buildSrc'

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        verifyBuildPathOperations(
            "Configure build",
            configureOps,
            [
                [":", root.id],
                [":buildB", configureOps[0].id],
                [":buildB:buildSrc", buildSrcOps[0].id],
                [":buildSrc", buildSrcOps[1].id]
            ]
        )

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        verifyTaskGraphOps(
            operations: treeTaskGraphOps,
            expectedParents: [buildSrcOps[0].id, buildSrcOps[1].id, root.id],
            expectedBuildPaths: [
                [":buildB:buildSrc", treeTaskGraphOps[0].id],
                [":buildSrc", treeTaskGraphOps[1].id],
                [":", treeTaskGraphOps[2].id],
                [":buildB", treeTaskGraphOps[2].id]
            ],
            extraBuildPathsWithCC: [":", ":buildB"]
        )

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 4
        runTasksOps[0].displayName == "Run tasks (:buildB:buildSrc)"
        runTasksOps[0].parentId == buildSrcOps[0].id
        runTasksOps[1].displayName == "Run tasks (:buildSrc)"
        runTasksOps[1].parentId == buildSrcOps[1].id
        // Build operations are run in parallel, so can appear in either order
        [runTasksOps[2].displayName, runTasksOps[3].displayName].sort() == ["Run tasks", "Run tasks (:buildB)"]
        runTasksOps[2].parentId == runMainTasks.id
        runTasksOps[3].parentId == runMainTasks.id

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyBuildPathOperations(
            "Notify task graph whenReady listeners",
            graphNotifyOps,
            [
                [":buildB:buildSrc", treeTaskGraphOps[0].id],
                [":buildSrc", treeTaskGraphOps[1].id],
                [":buildB", treeTaskGraphOps[2].id],
                [":", treeTaskGraphOps[2].id]
            ]
        )

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }
}
