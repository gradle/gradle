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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.gradle.operations.lifecycle.FinishRootBuildTreeBuildOperationType
import org.gradle.operations.lifecycle.RunRequestedWorkBuildOperationType

import static org.gradle.integtests.fixtures.TestableBuildOperationRecord.buildOp
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
        def allOps = operations.all(ExecuteTaskBuildOperationType)

        allOps.find { it.details.buildPath == ":buildB" && it.details.taskPath == ":jar" }
        allOps.find { it.details.buildPath == ":" && it.details.taskPath == ":jar" }

        allOps.each {
            assert operations.search(it, ExecuteTaskBuildOperationType).isEmpty()
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
        loadOps == [
            buildOp(displayName: "Load build", parent: root, details: [buildPath: ":"]),
            buildOp(displayName: "Load build (:buildB)", parent: loadOps[0], details: [buildPath: ":buildB"])
        ]

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath == [':', ":buildB"]

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps == [
            buildOp(displayName: "Configure build", parent: root, details: [buildPath: ":"]),
            buildOp(displayName: "Configure build (:buildB)", parent: configureOps[0], details: [buildPath: ":buildB"])
        ]

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        def expectedTreeTaskGraphOps = [
            buildOp(displayName: "Calculate build tree task graph", parent: root),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeTaskGraphOps << buildOp(displayName: "Calculate build tree task graph", parent: operations.only("Load configuration cache state"))
        }
        treeTaskGraphOps == expectedTreeTaskGraphOps

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphOps = [
            buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[0], details: ["buildPath": ":"]),
            buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB"])
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphOps += [
                buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[1], details: ["buildPath": ":"]),
                buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildB"])
            ]
        }
        taskGraphOps == expectedTaskGraphOps

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        def runTasksOps = operations.matchingRegex("Run tasks.*")
        // Build operations are run in parallel, so can appear in either order
        runTasksOps.sort { it.displayName } == [
            buildOp(displayName: "Run tasks", parent: runMainTasks),
            buildOp(displayName: "Run tasks (:buildB)", parent: runMainTasks)
        ]

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB)", parent: treeTaskGraphOps[0], details: [buildPath: ":buildB"]),
            buildOp(displayName: "Notify task graph whenReady listeners", parent: treeTaskGraphOps[0], details: [buildPath: ":"])
        ]

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
        def expectedTreeTaskGraphOps = [
            buildOp(displayName: "Calculate build tree task graph", parent: root),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeTaskGraphOps << buildOp(displayName: "Calculate build tree task graph", parent: operations.only("Load configuration cache state"))
        }
        treeTaskGraphOps == expectedTreeTaskGraphOps

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphOps = [
            buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[0], details: ["buildPath": ":"]),
            buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Calculate task graph (:buildC)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildC"])
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphOps += [
                buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[1], details: ["buildPath": ":"]),
                buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildB"]),
                buildOp(displayName: "Calculate task graph (:buildC)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildC"])
            ]
        }
        taskGraphOps == expectedTaskGraphOps

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB)", parent: treeTaskGraphOps[0], details: [buildPath: ":buildB"]),
            buildOp(displayName: "Notify task graph whenReady listeners (:buildC)", parent: treeTaskGraphOps[0], details: [buildPath: ":buildC"]),
            buildOp(displayName: "Notify task graph whenReady listeners", parent: treeTaskGraphOps[0], details: [buildPath: ":"])
        ]
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

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        def expectedTreeTaskGraphOps = [
            buildOp(displayName: "Calculate build tree task graph", parent: operations.first("Run included build logic build for build ':'")),
            buildOp(displayName: "Calculate build tree task graph", parent: root),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeTaskGraphOps << buildOp(displayName: "Calculate build tree task graph", parent: operations.only("Load configuration cache state"))
        }
        treeTaskGraphOps == expectedTreeTaskGraphOps

        def applyRootProjectBuildScript = operations.first("Apply build file 'build.gradle' to root project 'buildA'")
        applyRootProjectBuildScript in operations.parentsOf(treeTaskGraphOps[0])

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphOps = [
            buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Calculate task graph (:buildC)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildC"]),
            buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[1], details: ["buildPath": ":"]),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphOps += [
                buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[2], details: ["buildPath": ":"]),
            ]
        }
        taskGraphOps == expectedTaskGraphOps

        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB)", parent: treeTaskGraphOps[0], details: [buildPath: ":buildB"]),
            buildOp(displayName: "Notify task graph whenReady listeners (:buildC)", parent: treeTaskGraphOps[0], details: [buildPath: ":buildC"]),
            buildOp(displayName: "Notify task graph whenReady listeners", parent: treeTaskGraphOps[1], details: [buildPath: ":"])
        ]
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
        loadOps == [
            buildOp(displayName: "Load build", parent: root, details: [buildPath: ":"]),
            buildOp(displayName: "Load build (:buildB)", parent: loadOps[0], details: [buildPath: ":buildB"])
        ]

        def buildIdentifiedEvents = operations.progress(BuildIdentifiedProgressDetails)
        buildIdentifiedEvents*.details.buildPath == [':', ":buildB"]

        def configureOps = operations.all(ConfigureBuildBuildOperationType)
        configureOps == [
            buildOp(displayName: "Configure build", parent: root, details: [buildPath: ":"]),
            buildOp(displayName: "Configure build (:buildB)", parent: configureOps[0], details: [buildPath: ":buildB"])
        ]

        def treeTaskGraphOps = operations.all(CalculateTreeTaskGraphBuildOperationType)
        def expectedTreeTaskGraphOps = [
            buildOp(displayName: "Calculate build tree task graph", parent: operations.first("Run included build logic build for build ':'")),
            buildOp(displayName: "Calculate build tree task graph", parent: root),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTreeTaskGraphOps << buildOp(displayName: "Calculate build tree task graph", parent: operations.only("Load configuration cache state"))
        }
        treeTaskGraphOps == expectedTreeTaskGraphOps

        def applyRootProjectBuildScript = operations.first("Apply build file 'build.gradle' to root project 'buildA'")
        applyRootProjectBuildScript in operations.parentsOf(treeTaskGraphOps[0])

        def taskGraphOps = operations.all(CalculateTaskGraphBuildOperationType)
        def expectedTaskGraphOps = [
            buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[0], details: ["buildPath": ":buildB"]),
            buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[1], details: ["buildPath": ":"]),
            buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[1], details: ["buildPath": ":buildB"]),
        ]
        if (GradleContextualExecuter.configCache) {
            expectedTaskGraphOps += [
                buildOp(displayName: "Calculate task graph", parent: treeTaskGraphOps[2], details: ["buildPath": ":"]),
                buildOp(displayName: "Calculate task graph (:buildB)", parent: treeTaskGraphOps[2], details: ["buildPath": ":buildB"]),
            ]
        }
        taskGraphOps == expectedTaskGraphOps

        def runMainTasks = operations.only(RunRequestedWorkBuildOperationType)
        runMainTasks.parentId == root.id

        // Tasks are run for buildB multiple times, once for buildscript dependency and again for production dependency
        def runTasksOps = operations.matchingRegex("Run tasks.*")
        runTasksOps.size() == 3
        runTasksOps[0] == buildOp(displayName: "Run tasks (:buildB)", parent: operations.first("Run included build logic build for build ':'"))
        applyRootProjectBuildScript in operations.parentsOf(runTasksOps[0])
        // Build operations are run in parallel, so can appear in either order
        runTasksOps.takeRight(2).sort { it.displayName } == [
            buildOp(displayName: "Run tasks", parent: runMainTasks),
            buildOp(displayName: "Run tasks (:buildB)", parent: runMainTasks)
        ]

        // Task graph ready event sent only once
        def graphNotifyOps = operations.all(NotifyTaskGraphWhenReadyBuildOperationType)
        graphNotifyOps == [
            buildOp(displayName: "Notify task graph whenReady listeners (:buildB)", parent: treeTaskGraphOps[0], details: [buildPath: ":buildB"]),
            buildOp(displayName: "Notify task graph whenReady listeners", parent: treeTaskGraphOps[1], details: [buildPath: ":"])
        ]
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

}
