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
import spock.lang.IgnoreIf

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

    // Also covered by tests in configuration cache project
    @IgnoreIf({ GradleContextualExecuter.configCache })
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
        loadOps.size() == 3
        loadOps[0].displayName == "Load build"
        loadOps[0].details.buildPath == ":"
        loadOps[0].parentId == root.id

        loadOps[1].displayName == "Load build (:buildB)"
        loadOps[1].details.buildPath == ":buildB"
        loadOps[1].parentId == loadOps[0].id

        loadOps[2].displayName == "Load build (:buildB:buildSrc)"
        loadOps[2].details.buildPath == ":buildB:buildSrc"
        loadOps[2].parentId == buildSrcOps[0].id

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 3
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ':buildB'
        buildIdentifiedEvents[2].details.buildPath == ':buildB:buildSrc'

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps.size() == 3
        configureOps[0].displayName == "Configure build"
        configureOps[0].details.buildPath == ":"
        configureOps[0].parentId == root.id
        configureOps[1].displayName == "Configure build (:buildB)"
        configureOps[1].details.buildPath == ":buildB"
        configureOps[1].parentId == configureOps[0].id
        configureOps[2].displayName == "Configure build (:buildB:buildSrc)"
        configureOps[2].details.buildPath == ":buildB:buildSrc"
        configureOps[2].parentId == buildSrcOps[0].id

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        treeTaskGraphOps.size() == 2
        treeTaskGraphOps[0].displayName == "Calculate build tree task graph"
        treeTaskGraphOps[0].parentId == buildSrcOps[0].id
        treeTaskGraphOps[1].displayName == "Calculate build tree task graph"
        treeTaskGraphOps[1].parentId == root.id

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps.size() == 3
        taskGraphOps[0].displayName == "Calculate task graph (:buildB:buildSrc)"
        taskGraphOps[0].details.buildPath == ":buildB:buildSrc"
        taskGraphOps[0].parentId == treeTaskGraphOps[0].id
        taskGraphOps[1].displayName == "Calculate task graph"
        taskGraphOps[1].details.buildPath == ":"
        taskGraphOps[1].parentId == treeTaskGraphOps[1].id
        taskGraphOps[2].displayName == "Calculate task graph (:buildB)"
        taskGraphOps[2].details.buildPath == ":buildB"
        taskGraphOps[2].parentId == treeTaskGraphOps[1].id

        def runMainTasks = operations.first(Pattern.compile("Run main tasks"))
        runMainTasks.parentId == root.id

        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 3
        runTasksOps[0].displayName == "Run tasks (:buildB:buildSrc)"
        runTasksOps[0].parentId == buildSrcOps[0].id
        // Build operations are run in parallel, so can appear in either order
        [runTasksOps[1].displayName, runTasksOps[2].displayName].sort()  == ["Run tasks", "Run tasks (:buildB)"]
        runTasksOps[1].parentId == runMainTasks.id
        runTasksOps[2].parentId == runMainTasks.id

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps.size() == 3
        graphNotifyOps[0].displayName == 'Notify task graph whenReady listeners (:buildB:buildSrc)'
        graphNotifyOps[0].details.buildPath == ':buildB:buildSrc'
        graphNotifyOps[0].parentId == treeTaskGraphOps[0].id
        graphNotifyOps[1].displayName == "Notify task graph whenReady listeners (:buildB)"
        graphNotifyOps[1].details.buildPath == ":buildB"
        graphNotifyOps[1].parentId == treeTaskGraphOps[1].id
        graphNotifyOps[2].displayName == "Notify task graph whenReady listeners"
        graphNotifyOps[2].details.buildPath == ":"
        graphNotifyOps[2].parentId == treeTaskGraphOps[1].id

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }

    // Also covered by tests in configuration cache project
    @IgnoreIf({ GradleContextualExecuter.configCache })
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
        // TODO should have a buildPath associated
        buildSrcOps[1].displayName == "Build buildSrc"
        // TODO should have a buildPath associated

        def loadOps = operations.all(LoadBuildBuildOperationType)
        loadOps.size() == 4
        loadOps[0].displayName == "Load build"
        loadOps[0].details.buildPath == ":"
        loadOps[0].parentId == root.id

        loadOps[1].displayName == "Load build (:buildB)"
        loadOps[1].details.buildPath == ":buildB"
        loadOps[1].parentId == loadOps[0].id

        loadOps[2].displayName == "Load build (:buildB:buildSrc)"
        loadOps[2].details.buildPath == ":buildB:buildSrc"
        loadOps[2].parentId == buildSrcOps[0].id

        loadOps[3].displayName == "Load build (:buildSrc)"
        loadOps[3].details.buildPath == ":buildSrc"
        loadOps[3].parentId == buildSrcOps[1].id

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 4
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ':buildB'
        buildIdentifiedEvents[2].details.buildPath == ':buildB:buildSrc'
        buildIdentifiedEvents[3].details.buildPath == ':buildSrc'

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps.size() == 4
        configureOps[0].displayName == "Configure build"
        configureOps[0].details.buildPath == ":"
        configureOps[0].parentId == root.id
        configureOps[1].displayName == "Configure build (:buildB)"
        configureOps[1].details.buildPath == ":buildB"
        configureOps[1].parentId == configureOps[0].id
        configureOps[2].displayName == "Configure build (:buildB:buildSrc)"
        configureOps[2].details.buildPath == ":buildB:buildSrc"
        configureOps[2].parentId == buildSrcOps[0].id
        configureOps[3].displayName == "Configure build (:buildSrc)"
        configureOps[3].details.buildPath == ":buildSrc"
        configureOps[3].parentId == buildSrcOps[1].id

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        treeTaskGraphOps.size() == 3
        treeTaskGraphOps[0].displayName == "Calculate build tree task graph"
        treeTaskGraphOps[0].parentId == buildSrcOps[0].id
        treeTaskGraphOps[1].displayName == "Calculate build tree task graph"
        treeTaskGraphOps[1].parentId == buildSrcOps[1].id
        treeTaskGraphOps[2].displayName == "Calculate build tree task graph"
        treeTaskGraphOps[2].parentId == root.id

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps.size() == 4
        taskGraphOps[0].displayName == "Calculate task graph (:buildB:buildSrc)"
        taskGraphOps[0].details.buildPath == ":buildB:buildSrc"
        taskGraphOps[0].parentId == treeTaskGraphOps[0].id
        taskGraphOps[1].displayName == "Calculate task graph (:buildSrc)"
        taskGraphOps[1].details.buildPath == ":buildSrc"
        taskGraphOps[1].parentId == treeTaskGraphOps[1].id
        taskGraphOps[2].displayName == "Calculate task graph"
        taskGraphOps[2].details.buildPath == ":"
        taskGraphOps[2].parentId == treeTaskGraphOps[2].id
        taskGraphOps[3].displayName == "Calculate task graph (:buildB)"
        taskGraphOps[3].details.buildPath == ":buildB"
        taskGraphOps[3].parentId == treeTaskGraphOps[2].id

        def runMainTasks = operations.first(Pattern.compile("Run main tasks"))
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
        graphNotifyOps.size() == 4
        graphNotifyOps[0].displayName == "Notify task graph whenReady listeners (:buildB:buildSrc)"
        graphNotifyOps[0].details.buildPath == ":buildB:buildSrc"
        graphNotifyOps[0].parentId == treeTaskGraphOps[0].id
        graphNotifyOps[1].displayName == 'Notify task graph whenReady listeners (:buildSrc)'
        graphNotifyOps[1].details.buildPath == ':buildSrc'
        graphNotifyOps[1].parentId == treeTaskGraphOps[1].id
        graphNotifyOps[2].displayName == "Notify task graph whenReady listeners (:buildB)"
        graphNotifyOps[2].details.buildPath == ":buildB"
        graphNotifyOps[2].parentId == treeTaskGraphOps[2].id
        graphNotifyOps[3].displayName == "Notify task graph whenReady listeners"
        graphNotifyOps[3].details.buildPath == ":"
        graphNotifyOps[3].parentId == treeTaskGraphOps[2].id

        where:
        settings                     | display
        ""                           | "default root project name"
        "rootProject.name='someLib'" | "configured root project name"
    }
}
