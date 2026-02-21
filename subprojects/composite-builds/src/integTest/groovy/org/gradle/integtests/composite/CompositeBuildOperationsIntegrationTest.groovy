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

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.ConfigureBuildBuildOperationType
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.operations.lifecycle.FinishRootBuildTreeBuildOperationType
import org.gradle.operations.lifecycle.RunRequestedWorkBuildOperationType

import java.util.regex.Pattern

import static org.gradle.util.internal.TextUtil.getPlatformLineSeparator

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
        execute(buildA, ":jar")

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

    def "generates build lifecycle operations for included builds with #display"() {
        given:
        dependency "org.test:${dependencyName}:1.0"

        buildB.settingsFile << settings << "\n"

        when:
        execute(buildA, ":jar")

        then:
        executed ":buildB:jar"

        and:
        def root = operations.root(RunBuildBuildOperationType)

        def loadOps = operations.all(LoadBuildBuildOperationType)
        verifyBuildPathOperations(
            "Load build",
            loadOps,
            [
                [":", root.id],
                [":buildB", loadOps[0].id]
            ]
        )

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 2
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ":buildB"

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        verifyBuildPathOperations(
            "Configure build",
            configureOps,
            [
                [":", root.id],
                [":buildB", configureOps[0].id]
            ]
        )

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        verifyTaskGraphOps(
            operations: treeTaskGraphOps,
            expectedParents: [root.id],
            expectedBuildPaths: [
                [":", treeTaskGraphOps[0].id],
                [":buildB", treeTaskGraphOps[0].id]
            ]
        )

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 2
        // Build operations are run in parallel, so can appear in either order
        [runTasksOps[0].displayName, runTasksOps[1].displayName].sort() == ["Run tasks", "Run tasks (:buildB)"]
        runTasksOps[0].parentId == runMainTasks.id
        runTasksOps[1].parentId == runMainTasks.id

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyBuildPathOperations(
            "Notify task graph whenReady listeners",
            graphNotifyOps,
            [
                [":buildB", treeTaskGraphOps[0].id],
                [":", treeTaskGraphOps[0].id]
            ]
        )

        where:
        settings                     | dependencyName | display
        ""                           | "buildB"       | "default root project name"
        "rootProject.name='someLib'" | "someLib"      | "configured root project name"
    }

    def "generates build lifecycle operations for multiple included builds"() {
        given:
        def buildC = multiProjectBuild("buildC", ["someLib"]) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
            """
        }
        includedBuilds << buildC
        dependency "org.test:buildB:1.0"
        dependency "org.test:buildC:1.0"
        dependency buildB, "org.test:buildC:1.0"

        when:
        execute(buildA, ":jar")

        then:
        executed ":buildB:jar", ":buildC:jar"

        and:
        def root = operations.root(RunBuildBuildOperationType)

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        verifyTaskGraphOps(
            operations: treeTaskGraphOps,
            expectedParents: [root.id],
            expectedBuildPaths: [
                [":", treeTaskGraphOps[0].id],
                [":buildB", treeTaskGraphOps[0].id],
                [":buildC", treeTaskGraphOps[0].id]
            ]
        )

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyBuildPathOperations(
            "Notify task graph whenReady listeners",
            graphNotifyOps,
            [
                [":buildB", treeTaskGraphOps[0].id],
                [":buildC", treeTaskGraphOps[0].id],
                [":", treeTaskGraphOps[0].id]
            ],
        )
    }

    def "generates build lifecycle operations for multiple included builds used as buildscript dependencies"() {
        given:
        def buildC = multiProjectBuild("buildC", ["someLib"]) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
            """
        }
        includedBuilds << buildC
        buildA.buildFile.prepend("""
            buildscript {
                dependencies {
                    classpath 'org.test:buildB:1.0'
                    classpath 'org.test:buildC:1.0'
                }
            }
        """)
        dependency buildB, "org.test:buildC:1.0"

        when:
        execute(buildA, ":jar")

        then:
        executed ":buildB:jar", ":buildC:jar"

        and:
        def root = operations.root(RunBuildBuildOperationType)

        def applyRootProjectBuildScript = operations.first(Pattern.compile("Apply build file 'build.gradle' to root project 'buildA'"))

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        verifyTaskGraphOps(
            operations: treeTaskGraphOps,
            expectedParents: [operations.first("Run included build logic build for build ':'").id, root.id],
            expectedBuildPaths: [
                [":buildB", treeTaskGraphOps[0].id],
                [":buildC", treeTaskGraphOps[0].id],
                [":", treeTaskGraphOps[1].id]
            ],
            extraBuildPathsWithCC: [":"]
        )
        applyRootProjectBuildScript in operations.parentsOf(treeTaskGraphOps[0])

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyBuildPathOperations(
            "Notify task graph whenReady listeners",
            graphNotifyOps,
            [
                [":buildB", treeTaskGraphOps[0].id],
                [":buildC", treeTaskGraphOps[0].id],
                [":", treeTaskGraphOps[1].id]
            ]
        )
    }

    def "generates build lifecycle operations for included build used as buildscript and production dependency"() {
        given:
        buildA.buildFile.prepend("""
            buildscript {
                dependencies {
                    classpath 'org.test:b1:1.0'
                }
            }
        """)
        dependency "org.test:b2:1.0"

        when:
        execute(buildA, ":jar")

        then:
        executed ":buildB:b1:jar", ":buildB:b2:jar"

        and:
        def root = operations.root(RunBuildBuildOperationType)

        def loadOps = operations.all(LoadBuildBuildOperationType)
        verifyBuildPathOperations(
            "Load build",
            loadOps,
            [
                [":", root.id],
                [":buildB", loadOps[0].id]
            ]
        )

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents.size() == 2
        buildIdentifiedEvents[0].details.buildPath == ':'
        buildIdentifiedEvents[1].details.buildPath == ':buildB'

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        verifyBuildPathOperations(
            "Configure build",
            configureOps,
            [
                [":", root.id],
                [":buildB", configureOps[0].id]
            ]
        )

        def applyRootProjectBuildScript = operations.first(Pattern.compile("Apply build file 'build.gradle' to root project 'buildA'"))

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        verifyTaskGraphOps(
            operations: treeTaskGraphOps,
            expectedParents: [operations.first("Run included build logic build for build ':'").id, root.id],
            // The task graph for buildB is calculated multiple times, once for the buildscript dependency and again for the production dependency
            expectedBuildPaths: [
                [":buildB", treeTaskGraphOps[0].id],
                [":", treeTaskGraphOps[1].id],
                [":buildB", treeTaskGraphOps[1].id]
            ],
            extraBuildPathsWithCC: [":", ":buildB"]
        )
        applyRootProjectBuildScript in operations.parentsOf(treeTaskGraphOps[0])

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        // Tasks are run for buildB multiple times, once for buildscript dependency and again for production dependency
        def runTasksOps = operations.all(Pattern.compile("Run tasks.*"))
        runTasksOps.size() == 3
        runTasksOps[0].displayName == "Run tasks (:buildB)"
        applyRootProjectBuildScript in operations.parentsOf(runTasksOps[0])
        // Build operations are run in parallel, so can appear in either order
        [runTasksOps[1].displayName, runTasksOps[2].displayName].sort() == ["Run tasks", "Run tasks (:buildB)"]
        runTasksOps[1].parentId == runMainTasks.id
        runTasksOps[2].parentId == runMainTasks.id

        // Task graph ready event sent only once
        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyBuildPathOperations(
            "Notify task graph whenReady listeners",
            graphNotifyOps,
            [
                [":buildB", treeTaskGraphOps[0].id],
                [":", treeTaskGraphOps[1].id]
            ]
        )
    }

    def "generates finish build tree lifecycle operation for included builds without build finished operations"() {
        given:
        def buildC = multiProjectBuild("buildC", ["someLib"]) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
            """
        }
        includedBuilds << buildC
        buildA.buildFile.prepend("""
            buildscript {
                dependencies {
                    classpath 'org.test:buildB:1.0'
                    classpath 'org.test:buildC:1.0'
                }
            }
        """)
        dependency buildB, "org.test:buildC:1.0"

        when:
        execute(buildA, ":jar")

        then:
        executed ":buildB:jar", ":buildC:jar"

        and:
        operations.only(FinishRootBuildTreeBuildOperationType)
    }

    @UnsupportedWithConfigurationCache(because = "buildFinished", iterationMatchers = ".*with buildFinished.*")
    def "generates finish build tree lifecycle operation for included builds with #description"() {
        given:
        def buildC = multiProjectBuild("buildC", ["someLib"]) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
            """ << registration("buildC")
        }
        includedBuilds << buildC
        buildA.buildFile.prepend("""
            buildscript {
                dependencies {
                    classpath 'org.test:buildB:1.0'
                    classpath 'org.test:buildC:1.0'
                }
            }
        """)
        buildA.buildFile << registration("buildA")
        dependency buildB, "org.test:buildC:1.0"
        buildB.buildFile << registration("buildB")

        when:
        execute(buildA, ":jar")

        then:
        executed ":buildB:jar", ":buildC:jar"

        and:
        def buildFinished = operations.only(FinishRootBuildTreeBuildOperationType)
        buildFinished.progress.size() == 3
        buildFinished.progress.details.spans.text.flatten() ==~ ["buildA", "buildB", "buildC"].collect { "$message $it${getPlatformLineSeparator()}".toString() }

        where:
        description     | registration                                                          | message
        "buildFinished" | CompositeBuildOperationsIntegrationTest.&buildFinishedRegistrationFor | "buildFinished from"
        "flow actions"  | CompositeBuildOperationsIntegrationTest.&flowActionRegistrationFor    | "flowAction from"
    }

    def "build tree finished operation happens even when configuration fails"() {
        buildA.buildFile.text = """
            buildscript {
                dependencies {
                    classpath 'org.test:buildB:1.0'
                }
            }
        """ + buildA.buildFile.text
        buildB.file("src/main/java/Broken.java").text = "class Does not compile {}"
        when:
        fails(buildA, ":jar")

        then:
        executed ":buildB:compileJava"

        operations.none(RunRequestedWorkBuildOperationType)
        operations.only(FinishRootBuildTreeBuildOperationType)
    }

    def "generates finish build tree lifecycle operation for empty project"() {
        given:
        includedBuilds.clear()
        buildA.buildFile.text = ""

        when:
        execute(buildA, "help")

        then:
        executed ":help"

        and:
        operations.only(FinishRootBuildTreeBuildOperationType)
    }

    static String getFlowActionClass() {
        """
            abstract class LogBuild implements FlowAction<LogBuild.Parameters> {
                interface Parameters extends FlowParameters {
                    @Input
                    Property<String> getBuildName()
                }

                @Override
                void execute(Parameters parameters) {
                    println "flowAction from \${parameters.buildName.get()}"
                }
            }
        """
    }

    static String buildFinishedRegistrationFor(String buildName) {
        """
            gradle.buildFinished {
                println "buildFinished from $buildName"
            }
        """
    }

    static String flowActionRegistrationFor(String buildName) {
        flowActionClass + """
            def flowScope = gradle.services.get(FlowScope)
            def flowProviders = gradle.services.get(FlowProviders)
            flowScope.always(LogBuild) {
                parameters.buildName = flowProviders.buildWorkResult.map { result -> "$buildName" }
            }
        """
    }

    def assertChildrenNotIn(BuildOperationRecord origin, BuildOperationRecord op, List<BuildOperationRecord> allOps) {
        for (BuildOperationRecord child : op.children) {
            assert !allOps.contains(child): "Task operation $origin has child $child which is also a task operation"
            assertChildrenNotIn(origin, child, allOps)
        }
    }

}
