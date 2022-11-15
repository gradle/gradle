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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

import java.util.regex.Pattern

class SourceDependencyBuildOperationIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repo = new GitFileRepository('buildB', temporaryFolder.getTestDirectory())
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    @ToBeFixedForConfigurationCache
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

        def resolve = operations.first(ResolveConfigurationDependenciesBuildOperationType) { r -> r.details.buildPath == ":" && r.details.projectPath == ":" && r.details.configurationName == "compileClasspath" }
        resolve

        def loadOps = operations.all(LoadBuildBuildOperationType)
        loadOps.size() == 2
        loadOps[0].displayName == "Load build"
        loadOps[0].details.buildPath == ":"
        loadOps[0].parentId == root.id
        loadOps[1].displayName == "Load build (:buildB)"
        loadOps[1].details.buildPath == ":buildB"
        loadOps[1].parentId == resolve.id

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 2
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ':buildB'

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps.size() == 2
        configureOps[0].displayName == "Configure build"
        configureOps[0].details.buildPath == ":"
        configureOps[0].parentId == root.id
        configureOps[1].displayName == "Configure build (:${buildName})"
        configureOps[1].details.buildPath == ":${buildName}"
        configureOps[1].parentId == resolve.id

        def treeGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        treeGraphOps.size() == 1
        treeGraphOps[0].displayName == "Calculate build tree task graph"
        treeGraphOps[0].parentId == root.id

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps.size() == 2
        taskGraphOps[0].displayName == "Calculate task graph"
        taskGraphOps[0].details.buildPath == ":"
        taskGraphOps[0].parentId == treeGraphOps[0].id
        taskGraphOps[0].children.contains(resolve)
        taskGraphOps[1].displayName == "Calculate task graph (:${buildName})"
        taskGraphOps[1].details.buildPath == ":${buildName}"
        taskGraphOps[1].parentId == treeGraphOps[0].id

        def runMainTasks = operations.first(Pattern.compile("Run main tasks"))
        runMainTasks.parentId == root.id

        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 2
        // Build operations are run in parallel, so can appear in either order
        [runTasksOps[0].displayName, runTasksOps[1].displayName].sort() == ["Run tasks", "Run tasks (:${buildName})"]
        runTasksOps[0].parentId == runMainTasks.id
        runTasksOps[1].parentId == runMainTasks.id

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps.size() == 2
        graphNotifyOps[0].displayName == "Notify task graph whenReady listeners (:${buildName})"
        graphNotifyOps[0].details.buildPath == ":${buildName}"
        graphNotifyOps[0].parentId == treeGraphOps[0].id
        graphNotifyOps[1].displayName == 'Notify task graph whenReady listeners'
        graphNotifyOps[1].details.buildPath == ':'
        graphNotifyOps[1].parentId == treeGraphOps[0].id

        where:
        settings                     | buildName | dependencyName | display
        ""                           | "buildB"  | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "buildB"  | "someLib"      | "configured root project name"
    }
}
