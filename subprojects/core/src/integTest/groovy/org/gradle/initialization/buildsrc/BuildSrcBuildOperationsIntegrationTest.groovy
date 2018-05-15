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

import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.util.CollectionUtils
import spock.lang.Unroll

import java.util.regex.Pattern

class BuildSrcBuildOperationsIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def "includes build identifier in build operations with #display"() {
        given:
        def ops = new BuildOperationsFixture(executer, temporaryFolder)
        file("buildSrc/src/main/java/Thing.java") << "class Thing { }"
        file("buildSrc/settings.gradle") << settings << "\n"

        when:
        succeeds()

        then:
        def root = CollectionUtils.single(ops.roots())

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

        def configureOps = ops.all(ConfigureBuildBuildOperationType)
        configureOps.size() == 2
        configureOps[0].displayName == "Configure build (:buildSrc)"
        configureOps[0].details.buildPath == ":buildSrc"
        configureOps[0].parentId == buildSrcOps[0].id
        configureOps[1].displayName == "Configure build"
        configureOps[1].details.buildPath == ":"
        configureOps[1].parentId == root.id

        def taskGraphOps = ops.all(CalculateTaskGraphBuildOperationType)
        taskGraphOps.size() == 2
        taskGraphOps[0].displayName == "Calculate task graph (:buildSrc)"
        taskGraphOps[0].details.buildPath == ':buildSrc'
        taskGraphOps[0].parentId == buildSrcOps[0].id
        taskGraphOps[1].displayName == "Calculate task graph"
        taskGraphOps[1].details.buildPath == ':'
        taskGraphOps[1].parentId == root.id

        def runTasksOps = ops.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 2
        runTasksOps[0].displayName == "Run tasks (:buildSrc)"
        runTasksOps[0].parentId == buildSrcOps[0].id
        runTasksOps[1].displayName == "Run tasks"
        runTasksOps[1].parentId == root.id

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
}
