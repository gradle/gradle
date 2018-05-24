/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.util.CollectionUtils
import spock.lang.Unroll

import java.util.regex.Pattern

class CompositeBuildOperationsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
    }

    def "generates build operations for tasks in included builds"() {
        given:
        dependency 'org.test:buildB:1.0'

        when:
        execute(buildA, ":jar", [])

        then:
        executed ":buildB:jar"

        and:
        List<BuildOperationRecord> allOps = operations.all(ExecuteTaskBuildOperationType)

        allOps.find { it.details.buildPath == ":buildB" && it.details.taskPath == ":jar" }
        allOps.find { it.details.buildPath == ":" && it.details.taskPath == ":jar" }

        for (BuildOperationRecord operationRecord : allOps) {
            assertChildrenNotIn(operationRecord, operationRecord, allOps)
        }
    }

    @Unroll
    def "generates configure, task graph and run tasks operations for included builds with #display"() {
        given:
        dependency "org.test:${buildName}:1.0"

        buildB.settingsFile << settings << "\n"

        when:
        execute(buildA, ":jar", [])

        then:
        executed ":${buildName}:jar"

        and:
        def root = CollectionUtils.single(operations.roots())

        def loadOps = operations.all(LoadBuildBuildOperationType)
        loadOps.size() == 2
        loadOps[0].displayName == "Load build"
        loadOps[0].details.buildPath == ":"
        loadOps[0].parentId == root.id
        loadOps[1].displayName == "Load build (buildB)"
        loadOps[1].details.buildPath == ":${buildName}"
        loadOps[1].parentId == loadOps[0].id

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps.size() == 2
        configureOps[0].displayName == "Configure build"
        configureOps[0].details.buildPath == ":"
        configureOps[0].parentId == root.id
        configureOps[1].displayName == "Configure build (:${buildName})"
        configureOps[1].details.buildPath == ":${buildName}"
        configureOps[1].parentId == configureOps[0].id

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps.size() == 2
        taskGraphOps[0].displayName == "Calculate task graph"
        taskGraphOps[0].details.buildPath == ":"
        taskGraphOps[0].parentId == root.id
        taskGraphOps[1].displayName == "Calculate task graph (:${buildName})"
        taskGraphOps[1].details.buildPath == ":${buildName}"
        taskGraphOps[1].parentId == taskGraphOps[0].id

        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 2
        runTasksOps[0].displayName == "Run tasks"
        runTasksOps[0].parentId == root.id
        runTasksOps[1].displayName == "Run tasks (:${buildName})"
        runTasksOps[1].parentId == root.id

        where:
        settings                     | buildName | display
        ""                           | "buildB"  | "default root project name"
        "rootProject.name='someLib'" | "someLib" | "configured root project name"
    }

    def assertChildrenNotIn(BuildOperationRecord origin, BuildOperationRecord op, List<BuildOperationRecord> allOps) {
        for (BuildOperationRecord child : op.children) {
            assert !allOps.contains(child) : "Task operation $origin has child $child which is also a task operation"
            assertChildrenNotIn(origin, child, allOps)
        }
    }

}
