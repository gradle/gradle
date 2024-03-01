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

package org.gradle.configuration

import org.gradle.api.internal.plugins.ApplyPluginBuildOperationType
import org.gradle.api.internal.tasks.RegisterTaskBuildOperationType
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.configuration.internal.ExecuteListenerBuildOperationType
import org.gradle.configuration.project.NotifyProjectAfterEvaluatedBuildOperationType
import org.gradle.configuration.project.NotifyProjectBeforeEvaluatedBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.NotifyProjectsEvaluatedBuildOperationType
import org.gradle.initialization.NotifyProjectsLoadedBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationRecord.Progress
import org.gradle.test.fixtures.file.TestFile

class ExecuteUserLifecycleListenerBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def initFile = file('init.gradle')
    def subBuildFile = file('sub/build.gradle')
    def scriptFile = file('script.gradle')

    Long initScriptAppId
    Long initOtherScriptAppId
    Long settingsScriptAppId
    Long rootProjectScriptAppId
    Long rootOtherScriptAppId
    Long subProjectScriptAppId
    Long subOtherScriptAppId
    Long settingsPluginAppId
    Long rootProjectPluginAppId
    Long subProjectPluginAppId

    private void run() {
        def args = initFile.exists() ? ['-I', initFile.name, 'help'] : ['help']
        succeeds(*args)

        // useful for inspecting ops when things go wrong
        operations.debugTree({ op -> !op.hasDetailsOfType(RegisterTaskBuildOperationType.Details) })

        // verify that we don't get any duplicates in these
        sanityCheckApplicationIds()

        if (notEmpty(initFile)) {
            initScriptAppId = findScriptApplicationId(targetsGradle(), scriptFile(initFile))
        }
        if (hasScript(initFile, scriptFile.name)) {
            initOtherScriptAppId = findScriptApplicationId(targetsGradle(), scriptFile(scriptFile))
        }
        if (notEmpty(settingsFile)) {
            settingsScriptAppId = findScriptApplicationId(targetsSettings())
        }
        if (notEmpty(buildFile)) {
            rootProjectScriptAppId = findScriptApplicationId(targetsProject(':'), scriptFile(buildFile))
        }
        if (notEmpty(subBuildFile)) {
            subProjectScriptAppId = findScriptApplicationId(targetsProject(':sub'), scriptFile(subBuildFile))
        }
        if (hasPlugin(settingsFile, 'SettingsPlugin')) {
            settingsPluginAppId = findPluginApplicationId(targetsSettings())
        }
        if (hasPlugin(buildFile, 'ProjectPlugin')) {
            rootProjectPluginAppId = findPluginApplicationId(targetsProject(':'), pluginClass('ProjectPlugin'))
        }
        if (hasPlugin(subBuildFile, 'ProjectPlugin')) {
            subProjectPluginAppId = findPluginApplicationId(targetsProject(':sub'), pluginClass('ProjectPlugin'))
        }
        if (hasScript(buildFile, scriptFile.name)) {
            rootOtherScriptAppId = findScriptApplicationId(targetsProject(':'), scriptFile(scriptFile))
        }
        if (hasScript(subBuildFile, scriptFile.name)) {
            subOtherScriptAppId = findScriptApplicationId(targetsProject(':sub'), scriptFile(scriptFile))
        }
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def 'projectsLoaded listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source ->
            """
                gradle.projectsLoaded({
                    println "gradle.projectsLoaded(Action) from $source"
                } as Action)
                gradle.projectsLoaded {
                    println "gradle.projectsLoaded(Closure) from $source"
                }
                gradle.addListener(new BuildAdapter() {
                    void projectsLoaded(Gradle ignored) {
                        println "gradle.addListener(BuildListener) from $source"
                    }
                })
                gradle.addBuildListener(new BuildAdapter() {
                    void projectsLoaded(Gradle ignored) {
                        println "gradle.addBuildListener(BuildListener) from $source"
                    }
                })
            """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.projectsLoaded', 'gradle.projectsLoaded(Action)'),
            expectedOp('Gradle.projectsLoaded', 'gradle.projectsLoaded(Closure)'),
            expectedOp('Gradle.addListener', 'gradle.addListener(BuildListener)'),
            expectedOp('Gradle.addBuildListener', 'gradle.addBuildListener(BuildListener)')
        ]

        initFile << addGradleListeners('init')

        settingsFile << addGradleListeners('settings')
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        when:
        run()

        then:
        def projectsLoaded = operations.only(NotifyProjectsLoadedBuildOperationType, { it.details.buildPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(projectsLoaded, expectedGradleOps.size() * 3)
        verifyHasChildren(projectsLoaded, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(projectsLoaded, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(projectsLoaded, settingsPluginAppId, 'settings plugin', expectedGradleOps)
    }

    def 'rootProject listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source ->
            """
                gradle.rootProject({
                    println "gradle.rootProject(Action) from $source"
                } as Action)
                gradle.rootProject {
                    println "gradle.rootProject(Closure) from $source"
                }
            """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.rootProject', 'gradle.rootProject(Action)'),
            expectedOp('Gradle.rootProject', 'gradle.rootProject(Closure)'),
        ]

        initFile << addGradleListeners('init')

        settingsFile << addGradleListeners('settings')
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        when:
        run()

        then:
        def parent = operations.only("Execute 'rootProject {}' action").children.find { it.displayName == 'Cross-configure project :' }
        verifyExpectedNumberOfExecuteListenerChildren(parent, expectedGradleOps.size() * 3)
        verifyHasChildren(parent, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(parent, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(parent, settingsPluginAppId, 'settings plugin', expectedGradleOps)
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def 'projectsEvaluated listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source ->
            """
            gradle.projectsEvaluated({
                println "gradle.projectsEvaluated(Action) from $source"
            } as Action)
            gradle.projectsEvaluated {
                println "gradle.projectsEvaluated(Closure) from $source"
            }
            gradle.addListener(new BuildAdapter() {
                void projectsEvaluated(Gradle ignored) {
                    println "gradle.addListener(BuildListener) from $source"
                }
            })
            gradle.addBuildListener(new BuildAdapter() {
                void projectsEvaluated(Gradle ignored) {
                    println "gradle.addBuildListener(BuildListener) from $source"
                }
            })
        """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.projectsEvaluated', 'gradle.projectsEvaluated(Action)'),
            expectedOp('Gradle.projectsEvaluated', 'gradle.projectsEvaluated(Closure)'),
            expectedOp('Gradle.addListener', 'gradle.addListener(BuildListener)'),
            expectedOp('Gradle.addBuildListener', 'gradle.addBuildListener(BuildListener)')
        ]

        and:
        scriptFile << addGradleListeners("other script")

        initFile << addGradleListeners("init")

        settingsFile << addGradleListeners("settings")
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        buildFile << addGradleListeners("project script")
        applyInlinePlugin(buildFile, 'Project', addGradleListeners('project plugin'))
        applyScript(buildFile, scriptFile)

        when:
        run()

        then:
        def projectsEvaluated = operations.only(NotifyProjectsEvaluatedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsEvaluated, expectedGradleOps.size() * 6)
        verifyHasChildren(projectsEvaluated, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(projectsEvaluated, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(projectsEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOps)
        verifyHasChildren(projectsEvaluated, rootProjectScriptAppId, 'project script', expectedGradleOps)
        verifyHasChildren(projectsEvaluated, rootProjectPluginAppId, 'project plugin', expectedGradleOps)
        verifyHasChildren(projectsEvaluated, rootOtherScriptAppId, 'other script', expectedGradleOps)
    }

    def 'beforeEvaluate listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source ->
            """
            gradle.beforeProject({
                println "gradle.beforeProject(Action) from $source"
            } as Action)
            gradle.beforeProject {
                println "gradle.beforeProject(Closure) from $source"
            }
            gradle.addListener(new ProjectEvaluationListener() {
                void beforeEvaluate(Project p) {
                    println "gradle.addListener(ProjectEvaluationListener) from $source"
                }
                void afterEvaluate(Project p, ProjectState s) {
                }
            })
            gradle.addProjectEvaluationListener(new ProjectEvaluationListener() {
                void beforeEvaluate(Project p) {
                    println "gradle.addProjectEvaluationListener(ProjectEvaluationListener) from $source"
                }
                void afterEvaluate(Project p, ProjectState s) {
                }
            })
        """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.beforeProject', 'gradle.beforeProject(Action)'),
            expectedOp('Gradle.beforeProject', 'gradle.beforeProject(Closure)'),
            expectedOp('Gradle.addListener', 'gradle.addListener(ProjectEvaluationListener)'),
            expectedOp('Gradle.addProjectEvaluationListener', 'gradle.addProjectEvaluationListener(ProjectEvaluationListener)')
        ]
        def addProjectListeners = { String source, String target = null ->
            """
            ${target == null ? '' : "project('$target') { project ->"}
                project.beforeEvaluate({
                    println "project.beforeEvaluate(Action) from $source"
                } as Action)
                project.beforeEvaluate {
                    println "project.beforeEvaluate(Closure) from $source"
                }
            ${target == null ? '' : "}"}
        """
        }
        def expectedProjectOps = [
            expectedOp('Project.beforeEvaluate', 'project.beforeEvaluate(Action)'),
            expectedOp('Project.beforeEvaluate', 'project.beforeEvaluate(Closure)')
        ]

        and:
        scriptFile << addProjectListeners("other script")

        initFile << addGradleListeners('init')

        includeSub()
        settingsFile << addGradleListeners('settings')
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script', ':')
        buildFile << addProjectListeners('root project script', ':sub')
        applyInlinePlugin(buildFile, 'Project', addProjectListeners('root project plugin'))
        applyScript(buildFile, scriptFile)

        subBuildFile << addGradleListeners('sub project script')
        subBuildFile << addProjectListeners('sub project script', ':')
        subBuildFile << addProjectListeners('sub project script', ':sub')
        applyInlinePlugin(subBuildFile, 'Project', addProjectListeners('sub project plugin'))
        applyScript(subBuildFile, scriptFile)

        when:
        run()

        then:
        def rootBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootBeforeEvaluated, expectedGradleOps.size() * 3)
        verifyHasChildren(rootBeforeEvaluated, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(rootBeforeEvaluated, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(rootBeforeEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOps)
        // these all execute too late to catch any beforeProject/beforeEvaluate callbacks for itself
        verifyHasNoChildren(rootBeforeEvaluated, rootProjectScriptAppId)
        verifyHasNoChildren(rootBeforeEvaluated, rootProjectPluginAppId)
        verifyHasNoChildren(rootBeforeEvaluated, rootOtherScriptAppId)
        verifyHasNoChildren(rootBeforeEvaluated, subProjectScriptAppId)
        verifyHasNoChildren(rootBeforeEvaluated, subProjectPluginAppId)
        verifyHasNoChildren(rootBeforeEvaluated, subOtherScriptAppId)

        and:
        def subBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':sub' })
        verifyExpectedNumberOfExecuteListenerChildren(subBeforeEvaluated, expectedGradleOps.size() * 4 + expectedProjectOps.size())
        verifyHasChildren(subBeforeEvaluated, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(subBeforeEvaluated, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(subBeforeEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOps)
        verifyHasChildren(subBeforeEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOps + expectedProjectOps)
        // these execute too late to catch any beforeProject/beforeEvaluate callbacks for itself
        verifyHasNoChildren(subBeforeEvaluated, rootProjectPluginAppId)
        verifyHasNoChildren(subBeforeEvaluated, rootOtherScriptAppId)
        verifyHasNoChildren(subBeforeEvaluated, subProjectScriptAppId)
        verifyHasNoChildren(subBeforeEvaluated, subProjectPluginAppId)
        verifyHasNoChildren(subBeforeEvaluated, subOtherScriptAppId)
    }

    def 'afterEvaluate listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source ->
            """
            gradle.afterProject({
                println "gradle.afterProject(Action) from $source"
            } as Action)
            gradle.afterProject {
                println "gradle.afterProject(Closure(0)) from $source"
            }
            gradle.afterProject { passedProject ->
                println "gradle.afterProject(Closure(1)) from $source"
            }
            gradle.afterProject { passedProject, projectState ->
                println "gradle.afterProject(Closure(2)) from $source"
            }
            gradle.addListener(new ProjectEvaluationListener() {
                void beforeEvaluate(Project p) {
                }
                void afterEvaluate(Project p, ProjectState s) {
                    println "gradle.addListener(ProjectEvaluationListener) from $source"
                }
            })
            gradle.addProjectEvaluationListener(new ProjectEvaluationListener() {
                void beforeEvaluate(Project p) {
                }
                void afterEvaluate(Project p, ProjectState s) {
                    println "gradle.addProjectEvaluationListener(ProjectEvaluationListener) from $source"
                }
            })
        """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.afterProject', 'gradle.afterProject(Action)'),
            expectedOp('Gradle.afterProject', 'gradle.afterProject(Closure(0))'),
            expectedOp('Gradle.afterProject', 'gradle.afterProject(Closure(1))'),
            expectedOp('Gradle.afterProject', 'gradle.afterProject(Closure(2))'),
            expectedOp('Gradle.addListener', 'gradle.addListener(ProjectEvaluationListener)'),
            expectedOp('Gradle.addProjectEvaluationListener', 'gradle.addProjectEvaluationListener(ProjectEvaluationListener)')
        ]
        def addProjectListeners = { String source, String target = null ->
            """
            project${target == null ? '' : "('$target')"}.afterEvaluate({
                println "project.afterEvaluate(Action) from $source"
            } as Action)
            project.afterEvaluate {
                println "project.afterEvaluate(Closure) from $source"
            }
            """
        }
        def expectedProjectOps = [
            expectedOp('Project.afterEvaluate', 'project.afterEvaluate(Action)'),
            expectedOp('Project.afterEvaluate', 'project.afterEvaluate(Closure)')
        ]

        and:
        scriptFile << addProjectListeners("other script")

        initFile << addGradleListeners('init')

        includeSub()
        settingsFile << addGradleListeners('settings')
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script', ':')
        applyInlinePlugin(buildFile, 'Project', addProjectListeners('root project plugin'))
        applyScript(buildFile, scriptFile)

        subBuildFile << addGradleListeners('sub project script')
        subBuildFile << addProjectListeners('sub project script', ':sub')
        applyInlinePlugin(subBuildFile, 'Project', addProjectListeners('sub project plugin'))
        applyScript(subBuildFile, scriptFile)

        when:
        run()

        then:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, expectedGradleOps.size() * 4 + expectedProjectOps.size() * 3)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(rootAfterEvaluated, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(rootAfterEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOps)
        verifyHasChildren(rootAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOps + expectedProjectOps)
        verifyHasChildren(rootAfterEvaluated, rootProjectPluginAppId, 'root project plugin', expectedProjectOps)
        verifyHasChildren(rootAfterEvaluated, rootOtherScriptAppId, 'other script', expectedProjectOps)
        verifyHasNoChildren(rootAfterEvaluated, subProjectScriptAppId) // executed too late to catch any afterProject/afterEvaluate callbacks for earlier evaluated project
        verifyHasNoChildren(rootAfterEvaluated, subProjectPluginAppId) // we don't cross configure the plugin
        verifyHasNoChildren(rootAfterEvaluated, subOtherScriptAppId) // we don't cross configure the script

        and:
        def subAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':sub' })
        verifyExpectedNumberOfExecuteListenerChildren(subAfterEvaluated, expectedGradleOps.size() * 5 + expectedProjectOps.size() * 3)
        verifyHasChildren(subAfterEvaluated, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(subAfterEvaluated, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(subAfterEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOps)
        verifyHasChildren(subAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOps)
        verifyHasNoChildren(subAfterEvaluated, rootProjectPluginAppId) // we don't cross configure the plugin
        verifyHasNoChildren(subAfterEvaluated, rootOtherScriptAppId) // we don't cross configure the plugin
        verifyHasChildren(subAfterEvaluated, subProjectScriptAppId, 'sub project script', expectedGradleOps + expectedProjectOps)
        verifyHasChildren(subAfterEvaluated, subProjectPluginAppId, 'sub project plugin', expectedProjectOps)
        verifyHasChildren(subAfterEvaluated, subOtherScriptAppId, 'other script', expectedProjectOps)
    }

    def 'nested afterEvaluate listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source ->
            """
            gradle.afterProject { project ->
                println "gradle.afterProject(Closure) from $source"
                project.afterEvaluate {
                    println "nested gradle.afterProject(Closure) from $source"
                    project.afterEvaluate {
                        println "nested nested gradle.afterProject(Closure) from $source"
                    }
                }
            }
        """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.afterProject', 'gradle.afterProject(Closure)'),
            expectedOp('Project.afterEvaluate', 'nested gradle.afterProject(Closure)'),
            expectedOp('Project.afterEvaluate', 'nested nested gradle.afterProject(Closure)')
        ]
        def addProjectListeners = { String source ->
            """
            project.afterEvaluate {
                println "project.afterEvaluate(Closure) from $source"
                project.afterEvaluate {
                    println "nested project.afterEvaluate(Closure) from $source"
                    project.afterEvaluate {
                        println "nested nested project.afterEvaluate(Closure) from $source"
                    }
                }
            }
        """
        }
        def expectedProjectOps = [
            expectedOp('Project.afterEvaluate', 'project.afterEvaluate(Closure)'),
            expectedOp('Project.afterEvaluate', 'nested project.afterEvaluate(Closure)'),
            expectedOp('Project.afterEvaluate', 'nested nested project.afterEvaluate(Closure)')
        ]

        scriptFile << addProjectListeners('other script')

        initFile << addGradleListeners('init')

        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script')
        applyInlinePlugin(buildFile, 'Project', addProjectListeners('root project plugin'))
        applyScript(buildFile, scriptFile)

        when:
        run()

        then:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, expectedGradleOps.size() * 2 + expectedProjectOps.size() * 3)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(rootAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOps + expectedProjectOps)
        verifyHasChildren(rootAfterEvaluated, rootProjectPluginAppId, 'root project plugin', expectedProjectOps)
        verifyHasChildren(rootAfterEvaluated, rootOtherScriptAppId, 'other script', expectedProjectOps)
    }

    def 'taskGraph whenReady action listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source ->
            """
            gradle.addListener(new TaskExecutionGraphListener() {
                void graphPopulated(TaskExecutionGraph graph) {
                    println "gradle.addListener(TaskExecutionGraphListener) from $source"
                }
            })
            gradle.taskGraph.addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
                void graphPopulated(TaskExecutionGraph graph) {
                    println "gradle.taskGraph.addTaskExecutionGraphListener(TaskExecutionGraphListener) from $source"
                }
            })
            gradle.taskGraph.whenReady({
                println "gradle.taskGraph.whenReady(Action) from $source"
            } as Action)
            gradle.taskGraph.whenReady {
                println "gradle.taskGraph.whenReady(Closure) from $source"
            }
        """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.addListener', 'gradle.addListener(TaskExecutionGraphListener)'),
            expectedOp('TaskExecutionGraph.addTaskExecutionGraphListener', 'gradle.taskGraph.addTaskExecutionGraphListener(TaskExecutionGraphListener)'),
            expectedOp('TaskExecutionGraph.whenReady', 'gradle.taskGraph.whenReady(Action)'),
            expectedOp('TaskExecutionGraph.whenReady', 'gradle.taskGraph.whenReady(Closure)')
        ]

        scriptFile << addGradleListeners('other script')

        initFile << addGradleListeners('init')

        settingsFile << addGradleListeners('settings')

        buildFile << addGradleListeners('root project script')
        applyInlinePlugin(buildFile, 'Project', addGradleListeners('root project plugin'))
        applyScript(buildFile, scriptFile)

        when:
        run()

        then:
        def whenReadyEvaluated = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(whenReadyEvaluated, expectedGradleOps.size() * 5)
        verifyHasChildren(whenReadyEvaluated, initScriptAppId, 'init', expectedGradleOps)
        verifyHasChildren(whenReadyEvaluated, settingsScriptAppId, 'settings', expectedGradleOps)
        verifyHasChildren(whenReadyEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOps)
        verifyHasChildren(whenReadyEvaluated, rootProjectPluginAppId, 'root project plugin', expectedGradleOps)
        verifyHasChildren(whenReadyEvaluated, rootOtherScriptAppId, 'other script', expectedGradleOps)
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def 'listeners that implement multiple interfaces are decorated correctly'() {
        given:
        def addGradleListeners = { String source ->
            """
            class ComboListener implements BuildListener, ProjectEvaluationListener, TaskExecutionGraphListener {
                void beforeSettings(Settings settings) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
                void settingsEvaluated(Settings settings) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
                void projectsLoaded(Gradle gradle) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
                void projectsEvaluated(Gradle gradle) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
                void buildFinished(BuildResult result) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
                void beforeEvaluate(Project project) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
                void afterEvaluate(Project project, ProjectState state) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
                void graphPopulated(TaskExecutionGraph graph) {
                    println 'gradle.addListener(ComboListener) from $source'
                }
            }
            gradle.addListener(new ComboListener())
        """
        }
        def expectedGradleOps = [
            expectedOp('Gradle.addListener', 'gradle.addListener(ComboListener)')
        ]

        initFile << addGradleListeners('init')

        when:
        run()

        then:
        def projectsEvaluated = operations.only(NotifyProjectsEvaluatedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsEvaluated, 1)
        verifyHasChildren(projectsEvaluated, initScriptAppId, 'init', expectedGradleOps)

        and:
        def projectsLoaded = operations.only(NotifyProjectsLoadedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsLoaded, 1)
        verifyHasChildren(projectsLoaded, initScriptAppId, 'init', expectedGradleOps)

        and:
        def rootBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(rootBeforeEvaluated, 1)
        verifyHasChildren(rootBeforeEvaluated, initScriptAppId, 'init', expectedGradleOps)

        and:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, 1)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOps)

        and:
        def whenReadyEvaluated = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(whenReadyEvaluated, 1)
        verifyHasChildren(whenReadyEvaluated, initScriptAppId, 'init', expectedGradleOps)
    }

    def 'no extra executions for composite builds'() {
        // This test does two things:
        // - shake out internal listener registration that isn't using InternalListener.
        //   There are a lost of listeners registered through the methods that we've decorated in the composite build code
        // - sanity check application ids for the multi-build case
        given:
        file('buildSrc/build.gradle') << """
        """
        includeBuild()
        file('included/build.gradle') << """
            tasks.create('foo')
        """
        buildFile << "tasks.help.dependsOn(gradle.includedBuild('included').task(':foo'))"

        when:
        run()

        then:
        // no explicit listeners registered, we shouldn't get there
        operations.none(ExecuteListenerBuildOperationType)
    }

    def 'listener registrations in delayed callbacks are tracked correctly'() {
        given:
        def addBeforeProjectListeners = { String source ->
            """
            project.beforeEvaluate { ignored ->
                println "project.beforeEvaluate(Closure) from $source"
            }
        """
        }
        def addAfterProjectListeners = { String source ->
            """
            project.afterEvaluate { ignored ->
                println "project.afterEvaluate(Closure) from $source"
            }
        """
        }

        and:
        // put these in different scripts so that we can track that they report the correct registrant,
        // since allprojects callbacks are executed inside an internal rootProject listener
        initFile << """
            rootProject { project ->
                ${addBeforeProjectListeners('init file rootProject')}
            }
            apply from: file('$scriptFile.name')
        """
        scriptFile << """
            allprojects { project ->
                ${addAfterProjectListeners('script file allprojects')}
            }
        """

        includeSub()

        when:
        run()

        then:
        def rootBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootBeforeEvaluated, 1)
        verifyHasChildren(rootBeforeEvaluated, initScriptAppId, 'init file rootProject', [expectedOp('Project.beforeEvaluate', 'project.beforeEvaluate(Closure)')])

        and:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, 1)
        verifyHasChildren(rootAfterEvaluated, initOtherScriptAppId, 'script file allprojects', [expectedOp('Project.afterEvaluate', 'project.afterEvaluate(Closure)')])

        and:
        def subAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':sub' })
        verifyExpectedNumberOfExecuteListenerChildren(subAfterEvaluated, 1)
        verifyHasChildren(subAfterEvaluated, initOtherScriptAppId, 'script file allprojects', [expectedOp('Project.afterEvaluate', 'project.afterEvaluate(Closure)')])
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def 'decorated listener can be removed'() {
        given:
        initFile << """
            def listener = new BuildAdapter() {
                void projectsLoaded(Gradle ignored) {
                }
            }
            gradle.addListener(listener)
            gradle.removeListener(listener)
        """

        when:
        run()

        then:
        def projectsLoaded = operations.only(NotifyProjectsLoadedBuildOperationType)
        // listener should have been removed
        verifyExpectedNumberOfExecuteListenerChildren(projectsLoaded, 0)
    }

    def 'application ids are unique across gradleBuild builds'() {
        given:
        initFile << ""
        file('gb/settings.gradle').touch()
        file('gb/build.gradle') << ""
        buildFile << """
            tasks.help.dependsOn tasks.create('gb', GradleBuild) { gbTask ->
                dir = 'gb'
                gbTask.tasks = ['help']
            }
        """

        expect:
        run()
    }

    private static void verifyExpectedNumberOfExecuteListenerChildren(BuildOperationRecord op, int expectedChildren) {
        assert op.children.findAll { it.hasDetailsOfType(ExecuteListenerBuildOperationType.Details) }.size() == expectedChildren
    }

    private static List<BuildOperationRecord> verifyHasChildren(BuildOperationRecord op, long expectedApplicationId, String sourceName, List<ExpectedOperation> expectedOps) {
        def matchingChildren = op.children.findAll { it.hasDetailsOfType(ExecuteListenerBuildOperationType.Details) && it.details.applicationId == expectedApplicationId }
        verifyExpectedOps(matchingChildren, addSource(expectedOps, sourceName))
        matchingChildren
    }

    private static void verifyHasNoChildren(BuildOperationRecord op, long expectedApplicationId) {
        assert op.children.findAll { it.hasDetailsOfType(ExecuteListenerBuildOperationType.Details) && it.details.applicationId == expectedApplicationId }.empty
    }

    private static void verifyExpectedOps(List<BuildOperationRecord> ops, List<ExpectedOperation> expectedOps) {
        assert ops.size() == expectedOps.size()
        // no guarantees about listener execution order
        assert ops.find { op ->
            expectedOps.find { expectedOperation -> op.details.registrationPoint == expectedOperation.registrationPoint && progress(op) == expectedOperation.progressMessage } != null
        }
    }

    private static String progress(BuildOperationRecord op) {
        op.progress.collect { progress(it) }.join('').trim()
    }

    private static String progress(Progress p) {
        p.hasDetailsOfType(StyledTextOutputEvent) ? p.details.spans*.text.join('') : ''
    }

    private static boolean notEmpty(TestFile file) {
        file.exists() && file.length() > 0
    }

    private static boolean hasPlugin(TestFile file, String pluginName) {
        file.exists() && file.text.indexOf(pluginName) != -1
    }

    private static boolean hasScript(TestFile file, String scriptName) {
        file.exists() && (file.text.indexOf("apply from: rootProject.file('$scriptName')") != -1 || file.text.indexOf("apply from: file('$scriptName')") != -1)
    }

    private Long findOpApplicationId(Class<? extends BuildOperationType<?, ?>> opType, Spec<? super BuildOperationRecord> predicate) {
        operations.only(opType, predicate).details.applicationId as Long
    }

    private Long findScriptApplicationId(Spec<? super BuildOperationRecord>... predicates) {
        findOpApplicationId(ApplyScriptPluginBuildOperationType, Specs.intersect(predicates))
    }

    private Long findPluginApplicationId(Spec<? super BuildOperationRecord>... predicates) {
        findOpApplicationId(ApplyPluginBuildOperationType, Specs.intersect(predicates))
    }

    private static Spec<? super BuildOperationRecord> targetsGradle() {
        { s -> s.details.targetType == 'gradle' } as Spec<? super BuildOperationRecord>
    }

    private static Spec<? super BuildOperationRecord> targetsSettings() {
        { s -> s.details.targetType == 'settings' } as Spec<? super BuildOperationRecord>
    }

    private static Spec<? super BuildOperationRecord> targetsProject(String projectPath) {
        { s -> s.details.targetType == 'project' && s.details.targetPath == projectPath } as Spec<? super BuildOperationRecord>
    }

    private static Spec<? super BuildOperationRecord> pluginClass(String pluginClass) {
        { s -> s.details.pluginClass == pluginClass } as Spec<? super BuildOperationRecord>
    }

    private static Spec<? super BuildOperationRecord> scriptFile(TestFile file) {
        { s -> s.details.file == file.absolutePath } as Spec<? super BuildOperationRecord>
    }

    private void includeSub() {
        settingsFile << "include 'sub'"
        subBuildFile << ""
    }

    private void includeBuild() {
        settingsFile << "includeBuild './included'"
        subBuildFile << ""
    }

    private static String createPlugin(String pluginClassName, String targetType, String src) {
        """
            class ${pluginClassName} implements Plugin<${targetType}> {
                void apply(${targetType} ${targetType.toLowerCase()}) {
                    ${targetType != 'Gradle' ? "def gradle = ${targetType.toLowerCase()}.gradle" : ''}
                    $src
                }
            }
        """
    }

    private static void applyInlinePlugin(TestFile file, String targetType, String src) {
        def pluginClassName = "${targetType}Plugin"
        file << createPlugin(pluginClassName, targetType, src)
        file << "apply plugin: $pluginClassName\n"
    }

    private static void applyScript(TestFile targetBuildFile, TestFile scriptFile) {
        targetBuildFile << "apply from: rootProject.file('${scriptFile.name}')\n"
    }

    private void sanityCheckApplicationIds() {
        // verify that these are unique for each script / plugin application
        def pluginAppliedOps = operations.all(ApplyPluginBuildOperationType)
        def scriptAppliedOps = operations.all(ApplyScriptPluginBuildOperationType)
        assert ((pluginAppliedOps + scriptAppliedOps)*.details*.applicationId as Set).size() == pluginAppliedOps.size() + scriptAppliedOps.size()
    }

    private static expectedOp(String registrationPoint, String progressMessage) {
        return new ExpectedOperation(registrationPoint: registrationPoint, progressMessage: progressMessage)
    }

    private static List<ExpectedOperation> addSource(List<ExpectedOperation> tuples, String sourceName) {
        tuples.collect { expectedOp(it.registrationPoint, "$it.progressMessage from $sourceName") }
    }

    private static class ExpectedOperation {
        String registrationPoint
        String progressMessage
    }
}
