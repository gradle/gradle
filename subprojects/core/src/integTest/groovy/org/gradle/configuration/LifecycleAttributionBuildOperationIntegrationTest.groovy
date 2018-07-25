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

import org.gradle.api.internal.tasks.RegisterTaskBuildOperationType
import org.gradle.configuration.internal.ExecuteListenerBuildOperationType
import org.gradle.configuration.project.NotifyProjectAfterEvaluatedBuildOperationType
import org.gradle.configuration.project.NotifyProjectBeforeEvaluatedBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.NotifyProjectsEvaluatedBuildOperationType
import org.gradle.initialization.NotifyProjectsLoadedBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationRecord.Progress

class LifecycleAttributionBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def initFile = file('init.gradle')
    def subBuildFile = file('sub/build.gradle')

    Long initScriptAppId
    Long settingsScriptAppId
    Long rootProjectScriptAppId
    Long subProjectScriptAppId

    private void run() {
        succeeds '-I', initFile.name, 'help'
        // useful for inspecting ops when things go wrong
        operations.debugTree({ op -> !op.hasDetailsOfType(RegisterTaskBuildOperationType.Details) })
        initScriptAppId = operations.only(ApplyScriptPluginBuildOperationType, { s -> s.details.targetType == 'gradle'}).details.applicationId as Long
        if (settingsFile.file) {
            settingsScriptAppId = operations.only(ApplyScriptPluginBuildOperationType, { s -> s.details.targetType == 'settings' }).details.applicationId as Long
        }
        if (buildFile.file) {
            rootProjectScriptAppId = operations.only(ApplyScriptPluginBuildOperationType, { it.details.targetType == 'project' && it.details.targetPath == ':' }).details.applicationId as Long
        }
        if (subBuildFile.file) {
            subProjectScriptAppId = operations.only(ApplyScriptPluginBuildOperationType, { it.details.targetType == 'project' && it.details.targetPath == ':sub' }).details.applicationId as Long
        }
    }

    private void includeSub() {
        settingsFile << "include 'sub'"
        subBuildFile << ""
    }

    // plugin applied in settings
    // plugin applied in init script
    // plugin applied in project script
    // plugin applied in script applied from project script
    // applied in settings
    // applied in init script
    // applied in project script
    // applied in script applied from project script

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
                    void projectsLoaded(Gradle gradle) {
                        println "gradle.addListener(BuildListener) from $source"
                    }
                })
                gradle.addBuildListener(new BuildAdapter() {
                    void projectsLoaded(Gradle gradle) {
                        println "gradle.addBuildListener(BuildListener) from $source"
                    }
                })
            """
        }
        def expectedGradleOpProgressMessages = [
            'gradle.projectsLoaded(Action)',
            'gradle.projectsLoaded(Closure)',
            'gradle.addListener(BuildListener)',
            'gradle.addBuildListener(BuildListener)'
        ]
        initFile << addGradleListeners('init')
        settingsFile << addGradleListeners('settings')

        when:
        run()

        then:
        def projectsLoaded = operations.only(NotifyProjectsLoadedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsLoaded, 8)
        verifyHasChildren(projectsLoaded, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsLoaded, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
    }

    def 'projectsEvaluated listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source -> """
            gradle.projectsEvaluated({
                println "gradle.projectsEvaluated(Action) from $source"
            } as Action)
            gradle.projectsEvaluated {
                println "gradle.projectsEvaluated(Closure) from $source"
            }
            gradle.addListener(new BuildAdapter() {
                void projectsEvaluated(Gradle gradle) {
                    println "gradle.addListener(BuildListener) from $source"
                }
            })
            gradle.addBuildListener(new BuildAdapter() {
                void projectsEvaluated(Gradle gradle) {
                    println "gradle.addBuildListener(BuildListener) from $source"
                }
            })
        """}
        def expectedGradleOpProgressMessages = [
            'gradle.projectsEvaluated(Action)',
            'gradle.projectsEvaluated(Closure)',
            'gradle.addListener(BuildListener)',
            'gradle.addBuildListener(BuildListener)'
        ]

        initFile << addGradleListeners("init")
        settingsFile << addGradleListeners("settings")
        buildFile << addGradleListeners("project script")

        when:
        run()

        then:
        def projectsEvaluated = operations.only(NotifyProjectsEvaluatedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsEvaluated, 12)
        verifyHasChildren(projectsEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsEvaluated, rootProjectScriptAppId, 'project script', expectedGradleOpProgressMessages)
    }

    def 'beforeEvaluate listeners are attributed to the correct registrant'() {
        def addGradleListeners = { String source -> """
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
        """}
        def expectedGradleOpProgressMessages = [
            'gradle.beforeProject(Action)',
            'gradle.beforeProject(Closure)',
            'gradle.addListener(ProjectEvaluationListener)',
            'gradle.addProjectEvaluationListener(ProjectEvaluationListener)'
        ]
        def addProjectListeners = { String source, String target -> """
            project('$target') {
                beforeEvaluate({
                    println "project.beforeEvaluate(Action) from $source"
                } as Action)
                project.beforeEvaluate {
                    println "project.beforeEvaluate(Closure) from $source"
                }
            }
        """}
        def expectedProjectOpProgressMessages = [
            'project.beforeEvaluate(Action)',
            'project.beforeEvaluate(Closure)',
        ]

        includeSub()
        initFile << addGradleListeners('init')
        settingsFile << addGradleListeners('settings')
        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script', ':')
        buildFile << addProjectListeners('root project script', ':sub')
        subBuildFile << addGradleListeners('sub project script')
        subBuildFile << addProjectListeners('sub project script', ':')
        subBuildFile << addProjectListeners('sub project script', ':sub')

        when:
        run()

        then:
        def rootBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootBeforeEvaluated, 8)
        verifyHasChildren(rootBeforeEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(rootBeforeEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasNoChildren(rootBeforeEvaluated, rootProjectScriptAppId) // execute too late to catch any beforeProject/beforeEvaluate callbacks for itself
        verifyHasNoChildren(rootBeforeEvaluated, subProjectScriptAppId) // execute too late to catch any beforeProject/beforeEvaluate callbacks for later evaluated project

        and:
        def subBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':sub' })
        verifyExpectedNumberOfExecuteListenerChildren(subBeforeEvaluated, 14)
        verifyHasChildren(subBeforeEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(subBeforeEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(subBeforeEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        verifyHasNoChildren(subBeforeEvaluated, subProjectScriptAppId) // execute too late to catch any beforeProject/beforeEvaluate callbacks for itself
    }

    def 'afterEvaluate listeners are attributed to the correct registrant'() {
        def addGradleListeners = { String source -> """
            gradle.afterProject({
                println "gradle.afterProject(Action) from $source"
            } as Action)
            gradle.afterProject {
                println "gradle.afterProject(Closure) from $source"
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
        """}
        def expectedGradleOpProgressMessages = [
            'gradle.afterProject(Action)',
            'gradle.afterProject(Closure)',
            'gradle.addListener(ProjectEvaluationListener)',
            'gradle.addProjectEvaluationListener(ProjectEvaluationListener)'
        ]
        def addProjectListeners = { String source, String target -> """
            project('$target') {
                afterEvaluate({
                    println "project.afterEvaluate(Action) from $source"
                } as Action)
                project.afterEvaluate {
                    println "project.afterEvaluate(Closure) from $source"
                }
            }
        """}
        def expectedProjectOpProgressMessages = [
            'project.afterEvaluate(Action)',
            'project.afterEvaluate(Closure)',
        ]

        includeSub()
        initFile << addGradleListeners('init')
        settingsFile << addGradleListeners('settings')
        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script', ':')
        buildFile << addProjectListeners('root project script', ':sub')
        subBuildFile << addGradleListeners('sub project script')
        subBuildFile << addProjectListeners('sub project script', ':')
        subBuildFile << addProjectListeners('sub project script', ':sub')

        when:
        run()

        then:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, 14)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        verifyHasNoChildren(rootAfterEvaluated, subProjectScriptAppId) // executed too late to catch any afterProject/afterEvaluate callbacks for earlier evaluated project

        and:
        def subAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':sub' })
        verifyExpectedNumberOfExecuteListenerChildren(subAfterEvaluated, 20)
        verifyHasChildren(subAfterEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(subAfterEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(subAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        verifyHasChildren(subAfterEvaluated, subProjectScriptAppId, 'sub project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
    }

    def 'nested afterEvaluate listeners are attributed to the correct registrant'() {
        def addGradleListeners = { String source -> """
            gradle.afterProject { project ->
                println "gradle.afterProject(Closure) from $source"
                project.afterEvaluate {
                    println "nested gradle.afterProject(Closure) from $source"
                    project.afterEvaluate {
                        println "nested nested gradle.afterProject(Closure) from $source"
                    }
                }
            }
        """}
        def expectedGradleOpProgressMessages = [
            'gradle.afterProject(Closure)',
            'nested gradle.afterProject(Closure)',
            'nested nested gradle.afterProject(Closure)',
        ]
        def addProjectListeners = { String source -> """
            project.afterEvaluate {
                println "project.afterEvaluate(Closure) from $source"
                project.afterEvaluate {
                    println "nested project.afterEvaluate(Closure) from $source"
                    project.afterEvaluate {
                        println "nested nested project.afterEvaluate(Closure) from $source"
                    }
                }
            }
        """}
        def expectedProjectOpProgressMessages = [
            'project.afterEvaluate(Closure)',
            'nested project.afterEvaluate(Closure)',
            'nested nested project.afterEvaluate(Closure)',
        ]

        initFile << addGradleListeners('init')
        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script')

        when:
        run()

        then:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, 9)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
    }

    def 'taskGraph whenReady action listeners are attributed to the correct registrant'() {
        def addGradleListeners = { String source -> """
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
        """}
        def expectedGradleOpProgressMessages = [
            'gradle.addListener(TaskExecutionGraphListener)',
            'gradle.taskGraph.addTaskExecutionGraphListener(TaskExecutionGraphListener)',
            'gradle.taskGraph.whenReady(Action)',
            'gradle.taskGraph.whenReady(Closure)',
        ]

        initFile << addGradleListeners('init')
        settingsFile << addGradleListeners('settings')
        buildFile << addGradleListeners('root project script')

        when:
        run()

        then:
        def whenReadyEvaluated = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(whenReadyEvaluated, 12)
        verifyHasChildren(whenReadyEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(whenReadyEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(whenReadyEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages)
    }

    def 'listeners that implement multiple interfaces are decorated correctly'() {
        def addGradleListeners = { String source -> """
            class ComboListener implements BuildListener, ProjectEvaluationListener, TaskExecutionGraphListener {
                void buildStarted(Gradle gradle) {
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
        """}
        def expectedGradleOpProgressMessages = [
            'gradle.addListener(ComboListener)'
        ]

        initFile << addGradleListeners('init')

        when:
        run()

        then:
        def projectsEvaluated = operations.only(NotifyProjectsEvaluatedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsEvaluated, 1)
        verifyHasChildren(projectsEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)

        and:
        def projectsLoaded = operations.only(NotifyProjectsLoadedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsLoaded, 1)
        verifyHasChildren(projectsLoaded, initScriptAppId, 'init', expectedGradleOpProgressMessages)

        and:
        def rootBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(rootBeforeEvaluated, 1)
        verifyHasChildren(rootBeforeEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)

        and:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, 1)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)

        and:
        def whenReadyEvaluated = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(whenReadyEvaluated, 1)
        verifyHasChildren(whenReadyEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
    }

    // TODO:

    // composite builds coverage, lots of internal listener registration there

    // check that other buildListener methods aren't decorated

    // applied from plugins, non-project scripts

    // rootProject in init script ??
    // allprojects
    // others in Gradle interface?

    private static void verifyExpectedNumberOfExecuteListenerChildren(BuildOperationRecord op, int expectedChildren) {
        assert op.children.findAll { it.hasDetailsOfType(ExecuteListenerBuildOperationType.Details) }.size() == expectedChildren
    }

    private static List<BuildOperationRecord> verifyHasChildren(BuildOperationRecord op, long expectedApplicationId, String sourceName, List<String> expectedProgressMessages) {
        def matchingChildren = op.children.findAll { it.hasDetailsOfType(ExecuteListenerBuildOperationType.Details) && it.details .applicationId == expectedApplicationId}
        verifyExpectedOps(matchingChildren, expectedProgressMessages.collect { "$it from $sourceName" })
        matchingChildren
    }

    private void verifyHasNoChildren(BuildOperationRecord op, long expectedApplicationId) {
        assert op.children.findAll { it.hasDetailsOfType(ExecuteListenerBuildOperationType.Details) && it.details.applicationId == expectedApplicationId}.empty
    }

    private static void verifyExpectedOps(List<BuildOperationRecord> ops, List<String> expectedProgressMessages) {
        assert ops.size() == expectedProgressMessages.size()
        // no guarantees about listener execution order
        assert ops.collect { progress(it) } as Set == expectedProgressMessages as Set
    }

    private static String progress(BuildOperationRecord op) {
        op.progress.collect { progress(it) }.join('').trim()
    }

    private static String progress(Progress p) {
        p.hasDetailsOfType(StyledTextOutputEvent) ? p.details.spans*.text.join('') : ''
    }

}
