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
import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.util.CollectionUtils
import org.gradle.vcs.fixtures.GitFileRepository
import org.junit.Rule

import java.util.regex.Pattern

class SourceDependencyBuildOperationIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    GitFileRepository repo = new GitFileRepository('dep', temporaryFolder.getTestDirectory())
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "generates configure, task graph and run tasks operations for source dependency build"() {
        given:
        repo.file("settings.gradle") << """
            rootProject.name = 'buildB'
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
                    withModule("org.test:buildB") {
                        from(GitVersionControlSpec) {
                            url = uri("${repo.url}")
                        }
                    }
                }
            }
        """
        buildFile << """
            apply plugin: 'java'
            dependencies { implementation 'org.test:buildB:1.2' }
        """

        when:
        succeeds("assemble")

        then:
        def root = CollectionUtils.single(operations.roots())

        def resolve = operations.first(ResolveConfigurationDependenciesBuildOperationType) { r -> r.details.buildPath == ":" && r.details.projectPath == ":" && r.details.configurationName == "compileClasspath" }
        resolve

        def loadOps = operations.all(LoadBuildBuildOperationType)
        loadOps.size() == 2
        loadOps[0].displayName == "Load build"
        loadOps[0].details.buildPath == ":"
        loadOps[0].parentId == root.id
        loadOps[1].displayName == "Load build (dep)"
        // TODO - should have a buildPath associated
        loadOps[1].parentId == resolve.id

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps.size() == 2
        configureOps[0].displayName == "Configure build"
        configureOps[0].details.buildPath == ":"
        configureOps[0].parentId == root.id
        configureOps[1].displayName == "Configure build (:buildB)"
        configureOps[1].details.buildPath == ":buildB"
        configureOps[1].parentId == resolve.id

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps.size() == 2
        taskGraphOps[0].displayName == "Calculate task graph"
        taskGraphOps[0].details.buildPath == ":"
        taskGraphOps[0].parentId == root.id
        taskGraphOps[0].children.contains(resolve)
        taskGraphOps[1].displayName == "Calculate task graph (:buildB)"
        taskGraphOps[1].details.buildPath == ":buildB"
        taskGraphOps[1].parentId == taskGraphOps[0].id

        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 2
        runTasksOps[0].displayName == "Run tasks"
        runTasksOps[0].parentId == root.id
        runTasksOps[1].displayName == "Run tasks (:buildB)"
        runTasksOps[1].parentId == root.id
    }
}
