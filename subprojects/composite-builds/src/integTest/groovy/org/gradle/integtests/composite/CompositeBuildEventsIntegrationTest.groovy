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

import org.gradle.execution.MultipleBuildFailures
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.internal.exceptions.LocationAwareException

class CompositeBuildEventsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC

    def setup() {
        file('gradle-user-home/init.gradle') << """
            import org.gradle.api.Plugin
            import org.gradle.api.flow.FlowAction;
            import org.gradle.api.flow.FlowParameters;
            import org.gradle.api.flow.FlowProviders;
            import org.gradle.api.flow.FlowScope;
            import org.gradle.api.initialization.Settings
            import org.gradle.execution.MultipleBuildFailures
            import org.gradle.internal.exceptions.LocationAwareException
            
            import javax.inject.Inject;

            abstract class LoggingPlugin implements Plugin<Settings> {
            
                @Inject
                protected abstract FlowScope getFlowScope();
            
                @Inject
                protected abstract FlowProviders getFlowProviders();
            
                @Override
                void apply(Settings settings) {
                    def buildPath = settings.gradle.buildPath
            
                    getFlowScope().always(
                            LogBuildEventAction.class,
                            spec ->
                                    spec.getParameters().getEventToLog().set(
                                            getFlowProviders().getBuildWorkResult().map(result -> "buildFinished failure=\${Util.unpack(result.failure)} [\${buildPath}]")
                                    )
                    );
                }
            }

            abstract class LogBuildEventAction implements FlowAction<Parameters> {
                interface Parameters extends FlowParameters {
                    @Input
                    Property<String> getEventToLog();
                }
            
                @Override
                void execute(Parameters parameters) {
                    println("# \${parameters.eventToLog.get()}")
                }
            }

            class Util {
                static def unpack(java.util.Optional<Throwable> ot) {
                    if (!ot.isPresent()) {
                        return null
                    }
                    
                    def t = ot.get()
                    if (t instanceof ${LocationAwareException.name}) {
                        return unpack(java.util.Optional.ofNullable(t.cause))
                    } else if (t instanceof ${MultipleBuildFailures.name}) {
                        return t.causes.collect { unpack(java.util.Optional.ofNullable(it)) }
                    } else {
                        return t
                    }
                }
            }
            
            gradle.beforeSettings { settings ->
                settings.apply plugin: LoggingPlugin
            }
            gradle.taskGraph.whenReady {
                println '# taskGraphReady [' + gradle.buildPath + ']'
            }
            gradle.settingsEvaluated { settings -> 
                def buildName = settings.gradle.parent == null ? '' : settings.rootProject.name
                println '# settingsEvaluated [:' + buildName + ']'
            }
            gradle.projectsLoaded { gradle ->
                println '# projectsLoaded [' + gradle.buildPath + ']' 
            }
            gradle.projectsEvaluated { gradle ->
                println '# projectsEvaluated [' + gradle.buildPath + ']'
            }
"""

        buildA.buildFile << """
            task resolveArtifacts(type: Copy) {
                from configurations.compileClasspath
                into 'libs'
            }
"""

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB

        buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC
    }

    def "fires build listener events on included builds"() {
        given:
        dependency 'org.test:buildB:1.0'
        dependency buildB, 'org.test:buildC:1.0'

        when:
        execute()

        then:
        verifyBuildEvents()
    }

    def "fires build listener events for unused included builds"() {
        when:
        execute()

        then:
        events(13)
        loggedOncePerBuild('settingsEvaluated')
        loggedOncePerBuild('projectsLoaded')
        loggedOncePerBuild('projectsEvaluated')
        loggedOncePerBuild('taskGraphReady', [':'])
        loggedOncePerBuild('buildFinished failure=null')
    }

    def "fires build listener events for included build that provides buildscript and compile dependencies"() {
        given:
        def pluginBuild = pluginProjectBuild("pluginD")
        applyPlugin(buildA, "pluginD")
        includeBuild pluginBuild

        dependency 'org.test:b1:1.0'
        dependency(pluginBuild, 'org.test:b2:1.0')

        when:
        execute()

        then:
        events(19)
        loggedOncePerBuild('settingsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('projectsLoaded', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('projectsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('taskGraphReady', [':', ':buildB', ':pluginD'])
        loggedOncePerBuild('buildFinished failure=null', [':', ':buildB', ':buildC', ':pluginD'])
    }

    def "fires build listener events for included builds with additional discovered (compileOnly) dependencies"() {
        given:
        // BuildB will be initially evaluated with a single dependency on 'b1'.
        // Dependency on 'b2' is discovered while constructing the task graph for 'buildC'.
        dependency 'org.test:b1:1.0'
        dependency 'org.test:buildC:1.0'
        buildC.buildFile << """
            dependencies {
                compileOnly 'org.test:b2:1.0'
            }
"""

        when:
        execute()

        then:
        verifyBuildEvents()
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished")
    def "buildFinished for root build is guaranteed to complete after included builds"() {
        given:

        dependency 'org.test:b1:1.0'
        dependency 'org.test:buildC:1.0'
        buildC.buildFile << """
            dependencies {
                compileOnly 'org.test:b2:1.0'
            }

            gradle.buildFinished {
                sleep 500
            }
        """

        buildB.file("b2/build.gradle") << """
            task wait {
                doLast {
                    sleep 500
                }
            }

            jar.finalizedBy wait
        """

        when:
        execute()

        then:
        def outputLines = result.normalizedOutput.readLines()
        def rootBuildFinishedPosition = outputLines.indexOf("# buildFinished failure=null [:]")
        rootBuildFinishedPosition >= 0

        def buildBFinishedPosition = outputLines.indexOf("# buildFinished failure=null [:buildB]")
        buildBFinishedPosition >= 0
        def buildCFinishedPosition = outputLines.indexOf("# buildFinished failure=null [:buildC]")
        buildCFinishedPosition >= 0

        def buildSuccessfulPosition = outputLines.indexOf("BUILD SUCCESSFUL in 0s")
        buildSuccessfulPosition >= 0

        buildBFinishedPosition < rootBuildFinishedPosition
        buildBFinishedPosition < buildSuccessfulPosition

        buildCFinishedPosition < rootBuildFinishedPosition
        buildCFinishedPosition < buildSuccessfulPosition

        def lastRootBuildTaskPosition = outputLines.indexOf("> Task :resolveArtifacts")
        lastRootBuildTaskPosition >= 0

        def lateIncludedBuildTaskPosition = outputLines.indexOf("> Task :buildB:b2:wait")

        lateIncludedBuildTaskPosition < rootBuildFinishedPosition
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished")
    def "fires build finished events for all builds when settings script for child build cannot be compiled"() {
        given:
        buildA.settingsFile << """
            gradle.buildFinished {
                println "build A finished"
                throw new RuntimeException("build A broken")
            }
        """
        buildB.settingsFile << """
            broken!
        """
        buildC.settingsFile << """
            broken!
        """

        when:
        executeAndExpectFailure("help")

        then:
        outputContains("build A finished")

        events(3)
        loggedOncePerBuild("settingsEvaluated", [':'])
        // Root build also receives the failure from its children
        loggedOncePerBuild("buildFinished failure=org.gradle.groovy.scripts.ScriptCompilationException: Could not compile settings file '${buildB.settingsFile}'.", [':', ':buildB'])

        failure.assertHasFailures(2)
        failure.assertHasDescription("Could not compile settings file")
            .assertHasFileName("Settings file '${buildB.settingsFile}'")
            .assertHasLineNumber(19)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Settings file '${buildA.settingsFile}'")
            .assertHasLineNumber(19)
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished")
    def "fires build finished events for all builds when build script for child build fails"() {
        given:
        disableProblemsApiCheck()
        buildA.settingsFile << """
            gradle.buildFinished {
                println "build A finished"
                throw new RuntimeException("build A broken")
            }
        """
        buildB.buildFile << """
            gradle.buildFinished {
                println "build B finished"
            }
        """
        buildC.buildFile << """
            gradle.buildFinished {
                println "build C finished"
                throw new RuntimeException("build C broken")
            }

            throw new RuntimeException("failed in build C")
        """

        when:
        executeAndExpectFailure("help")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")

        events(10)
        loggedOncePerBuild("buildFinished failure=null", [':buildB'])
        loggedOncePerBuild("buildFinished failure=org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'.", [':buildC'])
        // Root build also receives the failures from its children, including the buildFinished { } failure
        loggedOncePerBuild("buildFinished failure=[org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'., java.lang.RuntimeException: build C broken]", [':'])

        failure.assertHasFailures(3)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Settings file '${buildA.settingsFile}'")
            .assertHasLineNumber(6)
        failure.assertHasCause("failed in build C")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(9)
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(6)
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished")
    def "fires build finished events for all builds when build finished event for other builds fail"() {
        given:
        disableProblemsApiCheck()
        buildA.buildFile << """
            gradle.buildFinished {
                println "build A finished"
                throw new RuntimeException("build A broken")
            }
        """
        buildB.buildFile << """
            gradle.buildFinished {
                println "build B finished"
                throw new RuntimeException("build B broken")
            }
        """
        buildC.buildFile << """
            gradle.buildFinished {
                println "build C finished"
                throw new RuntimeException("build C broken")
            }
        """

        when:
        executeAndExpectFailure("help")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")

        events(13)
        loggedOncePerBuild('buildFinished failure=null', [':buildB', ':buildC'])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild('buildFinished failure=[java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]', [':'])

        failure.assertHasFailures(3)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Build file '${buildA.buildFile}'")
            .assertHasLineNumber(14)
        failure.assertHasDescription("build B broken")
            .assertHasFileName("Build file '${buildB.buildFile}'")
            .assertHasLineNumber(8)
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(6)
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished")
    def "fires build finished events for all builds when other builds fail"() {
        given:
        disableProblemsApiCheck()
        buildA.buildFile << """
            gradle.buildFinished {
                println "build A finished"
                throw new RuntimeException("build A broken")
            }
            task broken {
                dependsOn gradle.includedBuild("buildB").task(":broken")
            }
        """
        buildB.buildFile << """
            gradle.buildFinished {
                println "build B finished"
                throw new RuntimeException("build B broken")
            }
            task broken {
                doLast { throw new RuntimeException("task broken") }
            }
        """
        buildC.buildFile << """
            gradle.buildFinished {
                println "build C finished"
                throw new RuntimeException("build C broken")
            }
        """

        when:
        executeAndExpectFailure("broken")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")

        events(14)
        loggedOncePerBuild("buildFinished failure=org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'.", [':buildB'])
        loggedOncePerBuild('buildFinished failure=null', [':buildC'])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild("buildFinished failure=[org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'., java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]", [':'])

        failure.assertHasFailures(4)
        failure.assertHasDescription("Execution failed for task ':buildB:broken'.")
            .assertHasFileName("Build file '${buildB.buildFile}'")
            .assertHasLineNumber(11)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Build file '${buildA.buildFile}'")
            .assertHasLineNumber(14)
        failure.assertHasDescription("build B broken")
            .assertHasFileName("Build file '${buildB.buildFile}'")
            .assertHasLineNumber(8)
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(6)
    }

    void verifyBuildEvents() {
        events(15) // 3 build * 5 events
        loggedOncePerBuild('settingsEvaluated')
        loggedOncePerBuild('projectsLoaded')
        loggedOncePerBuild('projectsEvaluated')
        loggedOncePerBuild('taskGraphReady')
        loggedOncePerBuild('buildFinished failure=null')
    }

    void loggedOncePerBuild(message, def builds = [':', ':buildB', ':buildC']) {
        builds.each { build ->
            loggedOnce("# $message [$build]")
        }
    }

    void events(int expected) {
        assert result.output.count("# ") == expected
    }

    void loggedOnce(String message) {
        outputContains(message)
        assert result.output.count(message) == 1
    }

    protected void execute() {
        super.execute(buildA, ":resolveArtifacts", ["-I../gradle-user-home/init.gradle"])
    }

    protected void executeAndExpectFailure(String task) {
        super.fails(buildA, task, ["-I../gradle-user-home/init.gradle"])
    }

}
