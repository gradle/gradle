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
import org.gradle.configuration.internal.ExecuteListenerBuildOperationType
import org.gradle.configuration.project.NotifyProjectAfterEvaluatedBuildOperationType
import org.gradle.configuration.project.NotifyProjectBeforeEvaluatedBuildOperationType
import org.gradle.execution.taskgraph.NotifyTaskGraphWhenReadyBuildOperationType
import org.gradle.initialization.NotifyProjectsEvaluatedBuildOperationType
import org.gradle.initialization.NotifyProjectsLoadedBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationRecord.Progress
import org.gradle.test.fixtures.file.TestFile

class LifecycleAttributionBuildOperationIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def initFile = file('init.gradle')
    def subBuildFile = file('sub/build.gradle')

    Long initScriptAppId
    Long settingsScriptAppId
    Long rootProjectScriptAppId
    Long subProjectScriptAppId
    Long settingsPluginAppId
    Long rootProjectPluginAppId
    Long subProjectPluginAppId

    private void run() {
        succeeds '-I', initFile.name, 'help'
        // useful for inspecting ops when things go wrong
        operations.debugTree({ op -> !op.hasDetailsOfType(RegisterTaskBuildOperationType.Details) })
        initScriptAppId = findScriptApplicationId(targetsGradle())
        if (settingsFile.file) {
            settingsScriptAppId = findScriptApplicationId(targetsSettings())
        }
        if (buildFile.file) {
            rootProjectScriptAppId = findScriptApplicationId(targetsProject(':'))
        }
        if (subBuildFile.file) {
            subProjectScriptAppId = findScriptApplicationId(targetsProject(':sub'))
        }
        if (hasPlugin(settingsFile, 'SettingsPlugin')) {
            settingsPluginAppId = findPluginApplicationId(targetsSettings())
        }
        if (hasPlugin(buildFile, 'ProjectPlugin')) {
            rootProjectPluginAppId = findPluginApplicationId(targetsProject(':', 'ProjectPlugin'))
        }
        if (hasPlugin(subBuildFile, 'ProjectPlugin')) {
            subProjectPluginAppId = findPluginApplicationId(targetsProject(':sub', 'ProjectPlugin'))
        }
    }

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
        def expectedGradleOpProgressMessages = [
            'gradle.projectsLoaded(Action)',
            'gradle.projectsLoaded(Closure)',
            'gradle.addListener(BuildListener)',
            'gradle.addBuildListener(BuildListener)'
        ]

        initFile << addGradleListeners('init')

        settingsFile << addGradleListeners('settings')
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        when:
        run()

        then:
        def projectsLoaded = operations.only(NotifyProjectsLoadedBuildOperationType, { it.details.buildPath == ':'})
        verifyExpectedNumberOfExecuteListenerChildren(projectsLoaded, expectedGradleOpProgressMessages.size() * 3)
        verifyHasChildren(projectsLoaded, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsLoaded, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsLoaded, settingsPluginAppId, 'settings plugin', expectedGradleOpProgressMessages)
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
                void projectsEvaluated(Gradle ignored) {
                    println "gradle.addListener(BuildListener) from $source"
                }
            })
            gradle.addBuildListener(new BuildAdapter() {
                void projectsEvaluated(Gradle ignored) {
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

        and:
        initFile << addGradleListeners("init")

        settingsFile << addGradleListeners("settings")
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        buildFile << addGradleListeners("project script")
        applyInlinePlugin(buildFile, 'Project', addGradleListeners('project plugin'))

        when:
        run()

        then:
        def projectsEvaluated = operations.only(NotifyProjectsEvaluatedBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(projectsEvaluated, expectedGradleOpProgressMessages.size() * 5)
        verifyHasChildren(projectsEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsEvaluated, rootProjectScriptAppId, 'project script', expectedGradleOpProgressMessages)
        verifyHasChildren(projectsEvaluated, rootProjectPluginAppId, 'project plugin', expectedGradleOpProgressMessages)
    }

    def 'beforeEvaluate listeners are attributed to the correct registrant'() {
        given:
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
        def addProjectListeners = { String source, String target = null -> """
            ${target == null ? '' : "project('$target') { project ->"}
                project.beforeEvaluate({
                    println "project.beforeEvaluate(Action) from $source"
                } as Action)
                project.beforeEvaluate {
                    println "project.beforeEvaluate(Closure) from $source"
                }
            ${target == null ? '' : "}"}
        """}
        def expectedProjectOpProgressMessages = [
            'project.beforeEvaluate(Action)',
            'project.beforeEvaluate(Closure)',
        ]

        and:
        initFile << addGradleListeners('init')

        includeSub()
        settingsFile << addGradleListeners('settings')
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script', ':')
        buildFile << addProjectListeners('root project script', ':sub')
        applyInlinePlugin(buildFile, 'Project', addProjectListeners('root project plugin'))

        subBuildFile << addGradleListeners('sub project script')
        subBuildFile << addProjectListeners('sub project script', ':')
        subBuildFile << addProjectListeners('sub project script', ':sub')
        applyInlinePlugin(subBuildFile, 'Project', addProjectListeners('sub project plugin'))

        when:
        run()

        then:
        def rootBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootBeforeEvaluated, expectedGradleOpProgressMessages.size() * 3)
        verifyHasChildren(rootBeforeEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(rootBeforeEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(rootBeforeEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOpProgressMessages)
        // these all execute too late to catch any beforeProject/beforeEvaluate callbacks for itself
        verifyHasNoChildren(rootBeforeEvaluated, rootProjectScriptAppId)
        verifyHasNoChildren(rootBeforeEvaluated, rootProjectPluginAppId)
        verifyHasNoChildren(rootBeforeEvaluated, subProjectScriptAppId)
        verifyHasNoChildren(rootBeforeEvaluated, subProjectPluginAppId)

        and:
        def subBeforeEvaluated = operations.only(NotifyProjectBeforeEvaluatedBuildOperationType, { it.details.projectPath == ':sub' })
        verifyExpectedNumberOfExecuteListenerChildren(subBeforeEvaluated, expectedGradleOpProgressMessages.size() * 4 + expectedProjectOpProgressMessages.size())
        verifyHasChildren(subBeforeEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(subBeforeEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(subBeforeEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOpProgressMessages)
        verifyHasChildren(subBeforeEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        // these execute too late to catch any beforeProject/beforeEvaluate callbacks for itself
        verifyHasNoChildren(subBeforeEvaluated, rootProjectPluginAppId)
        verifyHasNoChildren(subBeforeEvaluated, subProjectScriptAppId)
        verifyHasNoChildren(subBeforeEvaluated, subProjectPluginAppId)
    }

    def 'afterEvaluate listeners are attributed to the correct registrant'() {
        given:
        def addGradleListeners = { String source -> """
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
        """}
        def expectedGradleOpProgressMessages = [
            'gradle.afterProject(Action)',
            'gradle.afterProject(Closure(0))',
            'gradle.afterProject(Closure(1))',
            'gradle.afterProject(Closure(2))',
            'gradle.addListener(ProjectEvaluationListener)',
            'gradle.addProjectEvaluationListener(ProjectEvaluationListener)'
        ]
        def addProjectListeners = { String source, String target = null -> """
            ${target == null ? '' : "project('$target') { project ->"}
                project.afterEvaluate({
                    println "project.afterEvaluate(Action) from $source"
                } as Action)
                project.afterEvaluate {
                    println "project.afterEvaluate(Closure) from $source"
                }
            ${target == null ? '' : '}'}
        """}
        def expectedProjectOpProgressMessages = [
            'project.afterEvaluate(Action)',
            'project.afterEvaluate(Closure)',
        ]

        and:
        initFile << addGradleListeners('init')

        includeSub()
        settingsFile << addGradleListeners('settings')
        applyInlinePlugin(settingsFile, 'Settings', addGradleListeners('settings plugin'))

        buildFile << addGradleListeners('root project script')
        buildFile << addProjectListeners('root project script', ':')
        buildFile << addProjectListeners('root project script', ':sub')
        applyInlinePlugin(buildFile, 'Project', addProjectListeners('root project plugin'))

        subBuildFile << addGradleListeners('sub project script')
        subBuildFile << addProjectListeners('sub project script', ':')
        subBuildFile << addProjectListeners('sub project script', ':sub')
        applyInlinePlugin(subBuildFile, 'Project', addProjectListeners('sub project plugin'))

        when:
        run()

        then:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, expectedGradleOpProgressMessages.size() * 4 + expectedProjectOpProgressMessages.size() * 2)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, rootProjectPluginAppId, 'root project plugin', expectedProjectOpProgressMessages)
        verifyHasNoChildren(rootAfterEvaluated, subProjectScriptAppId) // executed too late to catch any afterProject/afterEvaluate callbacks for earlier evaluated project
        verifyHasNoChildren(rootAfterEvaluated, subProjectPluginAppId) // we don't cross configure the plugin

        and:
        def subAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':sub' })
        verifyExpectedNumberOfExecuteListenerChildren(subAfterEvaluated, expectedGradleOpProgressMessages.size() * 5 + expectedProjectOpProgressMessages.size() * 3)
        verifyHasChildren(subAfterEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(subAfterEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(subAfterEvaluated, settingsPluginAppId, 'settings plugin', expectedGradleOpProgressMessages)
        verifyHasChildren(subAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        verifyHasNoChildren(subAfterEvaluated, rootProjectPluginAppId) // we don't cross configure the plugin
        verifyHasChildren(subAfterEvaluated, subProjectScriptAppId, 'sub project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        verifyHasChildren(subAfterEvaluated, subProjectPluginAppId, 'sub project plugin', expectedProjectOpProgressMessages)
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
        applyInlinePlugin(buildFile, 'Project', addProjectListeners('root project plugin'))

        when:
        run()

        then:
        def rootAfterEvaluated = operations.only(NotifyProjectAfterEvaluatedBuildOperationType, { it.details.projectPath == ':' })
        verifyExpectedNumberOfExecuteListenerChildren(rootAfterEvaluated, expectedGradleOpProgressMessages.size() * 2 + expectedProjectOpProgressMessages.size() * 2)
        verifyHasChildren(rootAfterEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages + expectedProjectOpProgressMessages)
        verifyHasChildren(rootAfterEvaluated, rootProjectPluginAppId, 'root project plugin', expectedProjectOpProgressMessages)
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
        applyInlinePlugin(buildFile, 'Project', addGradleListeners('root project plugin'))

        when:
        run()

        then:
        def whenReadyEvaluated = operations.only(NotifyTaskGraphWhenReadyBuildOperationType)
        verifyExpectedNumberOfExecuteListenerChildren(whenReadyEvaluated, expectedGradleOpProgressMessages.size() * 4)
        verifyHasChildren(whenReadyEvaluated, initScriptAppId, 'init', expectedGradleOpProgressMessages)
        verifyHasChildren(whenReadyEvaluated, settingsScriptAppId, 'settings', expectedGradleOpProgressMessages)
        verifyHasChildren(whenReadyEvaluated, rootProjectScriptAppId, 'root project script', expectedGradleOpProgressMessages)
        verifyHasChildren(whenReadyEvaluated, rootProjectPluginAppId, 'root project plugin', expectedGradleOpProgressMessages)
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

    // applied non-project scripts

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


    private static boolean hasPlugin(TestFile file, String pluginName) {
        file.exists() && file.text.indexOf(pluginName) != -1
    }

    private Long findOpApplicationId(Class<? extends BuildOperationType<?,?>> opType, Spec<? super BuildOperationRecord> predicate) {
        operations.only(opType, predicate).details.applicationId as Long
    }

    private Long findScriptApplicationId(Spec<? super BuildOperationRecord> predicate) {
        findOpApplicationId(ApplyScriptPluginBuildOperationType, predicate)
    }

    private Long findPluginApplicationId(Spec<? super BuildOperationRecord> predicate) {
        findOpApplicationId(ApplyPluginBuildOperationType, predicate)
    }

    private static Spec<? super BuildOperationRecord> targetsGradle() {
        { s -> s.details.targetType == 'gradle'} as Spec<? super BuildOperationRecord>
    }

    private static Spec<? super BuildOperationRecord> targetsSettings() {
        { s -> s.details.targetType == 'settings'} as Spec<? super BuildOperationRecord>
    }

    private static Spec<? super BuildOperationRecord> targetsProject(String projectPath, String pluginClass = null) {
        { s -> s.details.targetType == 'project' && s.details.targetPath == projectPath && (!pluginClass || s.details.pluginClass == pluginClass)} as Spec<? super BuildOperationRecord>
    }

    private void includeSub() {
        settingsFile << "include 'sub'"
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

    private void applyInlinePlugin(TestFile file, String targetType, String src) {
        def pluginClassName = "${targetType}Plugin"
        file << createPlugin(pluginClassName, targetType, src)
        file << "apply plugin: $pluginClassName"
    }

}
