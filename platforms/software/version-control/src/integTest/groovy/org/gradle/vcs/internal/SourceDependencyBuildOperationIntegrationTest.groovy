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

package org.gradle.vcs.internal

import org.gradle.api.internal.artifacts.configurations.ResolveConfigurationDependenciesBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.operations.lifecycle.RunRequestedWorkBuildOperationType
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

import static org.gradle.integtests.fixtures.TestableBuildOperationRecord.buildOp

class SourceDependencyBuildOperationIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repo = new GitFileRepository('buildB', temporaryFolder.getTestDirectory())
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "generates configure, task graph and run tasks operations for source dependency build with #display"() {
        given:
        repo.file("settings.gradle") << """
            ${settings}
        """
        repo.file("build.gradle") << """
            apply plugin: 'java'
            group = 'org.test'
            version = '1.2'
        """
        repo.commit("initial version")
        repo.createLightWeightTag("1.2")

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("org.test:${dependencyName}") {
                        from(GitVersionControlSpec) {
                            url = uri("${repo.url}")
                        }
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'java'
            dependencies { implementation 'org.test:${dependencyName}:1.2' }
        """

        when:
        succeeds("assemble")

        then:
        def root = operations.root(RunBuildBuildOperationType)

        def resolve = operations.first(ResolveConfigurationDependenciesBuildOperationType) { r ->
            r.details.buildPath == ":" && r.details.projectPath == ":" && r.details.configurationName == "compileClasspath"
        }
        resolve

        def loadOps = operations.all(LoadBuildBuildOperationType)
        loadOps == [
            buildOp(displayName: "Load build", parent: root, details: [buildPath: ":"]),
            buildOp(displayName: "Load build (:buildB)", parent: resolve, details: [buildPath: ":${buildName}"]),
        ]

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath == [":", ":buildB"]

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps == [
            buildOp(displayName: "Configure build", parent: root, details: [buildPath: ":"]),
            buildOp(displayName: "Configure build (:${buildName})", parent: resolve, details: [buildPath: ":${buildName}"]),
        ]

        def treeGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        treeGraphOps == [
            buildOp(displayName: "Calculate build tree task graph", parent: root)
        ]

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps == [
            buildOp(displayName: "Calculate task graph", parent: treeGraphOps[0], details: [buildPath: ":"]),
            buildOp(displayName: "Calculate task graph (:${buildName})", parent: treeGraphOps[0], details: [buildPath: ":${buildName}"]),
        ]
        resolve.parentId == taskGraphOps[0].id

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = operations.matchingRegex("Run tasks.*")
        runTasksOps.size() == 2
        // Build operations are run in parallel, so can appear in either order
        runTasksOps.sort { it.displayName } == [
            buildOp(displayName: "Run tasks", parent: runMainTasks),
            buildOp(displayName: "Run tasks (:${buildName})", parent: runMainTasks)
        ]

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: "Notify task graph whenReady listeners (:${buildName})", parent: treeGraphOps[0], details: [buildPath: ":${buildName}"]),
            buildOp(displayName: 'Notify task graph whenReady listeners', parent: treeGraphOps[0], details: [buildPath: ':'])
        ]

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }
}
