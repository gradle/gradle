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
            class Util {
                static def unpack(Throwable t) {
                    if (t instanceof ${LocationAwareException.name}) {
                        return unpack(t.cause)
                    } else if (t instanceof ${MultipleBuildFailures.name}) {
                        return t.causes.collect { unpack(it) }
                    } else {
                        return t
                    }
                }
            }

            gradle.buildFinished { result ->
                println '# gradle.buildFinished failure=' + Util.unpack(result.failure) + ' [' + gradle.identityPath + ']'
            }
            gradle.taskGraph.whenReady {
                println '# gradle.taskGraphReady [' + gradle.identityPath + ']'
            }
            gradle.addBuildListener(new LoggingBuildListener())
            class LoggingBuildListener extends BuildAdapter {
                void settingsEvaluated(Settings settings) {
                    def buildName = settings.gradle.parent == null ? '' : settings.rootProject.name
                    println '# buildListener.settingsEvaluated [:' + buildName + ']'
                }
                void projectsLoaded(Gradle gradle) {
                    println '# buildListener.projectsLoaded [' + gradle.identityPath + ']'
                }
                void projectsEvaluated(Gradle gradle) {
                    println '# buildListener.projectsEvaluated [' + gradle.identityPath + ']'
                }
                void buildFinished(BuildResult result) {
                    println '# buildListener.buildFinished failure=' + Util.unpack(result.failure) + ' [' + result.gradle.identityPath + ']'
                }
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

    @ToBeFixedForConfigurationCache(because = "build listener")
    def "fires build listener events on included builds"() {
        given:
        dependency 'org.test:buildB:1.0'
        dependency buildB, 'org.test:buildC:1.0'

        when:
        execute()

        then:
        verifyBuildEvents()
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def "fires build listener events for unused included builds"() {
        when:
        execute()

        then:
        events(16)
        loggedOncePerBuild('buildListener.settingsEvaluated')
        loggedOncePerBuild('buildListener.projectsLoaded')
        loggedOncePerBuild('buildListener.projectsEvaluated')
        loggedOncePerBuild('gradle.taskGraphReady', [':'])
        loggedOncePerBuild('buildListener.buildFinished failure=null')
        loggedOncePerBuild('gradle.buildFinished failure=null')
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
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
        events(23)
        loggedOncePerBuild('buildListener.settingsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('buildListener.projectsLoaded', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('buildListener.projectsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('gradle.taskGraphReady', [':', ':buildB', ':pluginD'])
        loggedOncePerBuild('buildListener.buildFinished failure=null', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('gradle.buildFinished failure=null', [':', ':buildB', ':buildC', ':pluginD'])
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
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

    @ToBeFixedForConfigurationCache(because = "build listener")
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
        def rootBuildFinishedPosition = outputLines.indexOf("# gradle.buildFinished failure=null [:]")
        rootBuildFinishedPosition >= 0

        def buildBFinishedPosition = outputLines.indexOf("# gradle.buildFinished failure=null [:buildB]")
        buildBFinishedPosition >= 0
        def buildCFinishedPosition = outputLines.indexOf("# gradle.buildFinished failure=null [:buildC]")
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

        events(5)
        loggedOncePerBuild("buildListener.settingsEvaluated", [':'])
        // Root build also receives the failure from its children
        loggedOncePerBuild("buildListener.buildFinished failure=org.gradle.groovy.scripts.ScriptCompilationException: Could not compile settings file '${buildB.settingsFile}'.", [':', ':buildB'])
        loggedOncePerBuild("gradle.buildFinished failure=org.gradle.groovy.scripts.ScriptCompilationException: Could not compile settings file '${buildB.settingsFile}'.", [':', ':buildB'])

        failure.assertHasFailures(2)
        failure.assertHasDescription("Could not compile settings file")
            .assertHasFileName("Settings file '${buildB.settingsFile}'")
            .assertHasLineNumber(7)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Settings file '${buildA.settingsFile}'")
            .assertHasLineNumber(6)
    }

    def "fires build finished events for all builds when build script for child build fails"() {
        given:
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

        events(13)
        loggedOncePerBuild("buildListener.buildFinished failure=null", [':buildB'])
        loggedOncePerBuild("gradle.buildFinished failure=null", [':buildB'])
        loggedOncePerBuild("buildListener.buildFinished failure=org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'.", [':buildC'])
        loggedOncePerBuild("gradle.buildFinished failure=org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'.", [':buildC'])
        // Root build also receives the failures from its children, including the buildFinished { } failure
        loggedOncePerBuild("buildListener.buildFinished failure=[org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'., java.lang.RuntimeException: build C broken]", [':'])
        loggedOncePerBuild("gradle.buildFinished failure=[org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'., java.lang.RuntimeException: build C broken]", [':'])

        failure.assertHasFailures(3)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Settings file '${buildA.settingsFile}'")
            .assertHasLineNumber(6)
        failure.assertHasCause("failed in build C")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(12)
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(9)
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def "fires build finished events for all builds when build finished event for other builds fail"() {
        given:
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

        events(16)
        loggedOncePerBuild('buildListener.buildFinished failure=null', [':buildB', ':buildC'])
        loggedOncePerBuild('gradle.buildFinished failure=null', [':buildB', ':buildC'])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild('buildListener.buildFinished failure=[java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]', [':'])
        loggedOncePerBuild('gradle.buildFinished failure=[java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]', [':'])

        failure.assertHasFailures(3)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Build file '${buildA.buildFile}'")
            .assertHasLineNumber(17)
        failure.assertHasDescription("build B broken")
            .assertHasFileName("Build file '${buildB.buildFile}'")
            .assertHasLineNumber(13)
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(9)
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def "fires build finished events for all builds when other builds fail"() {
        given:
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

        events(17)
        loggedOncePerBuild("buildListener.buildFinished failure=org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'.", [':buildB'])
        loggedOncePerBuild("gradle.buildFinished failure=org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'.", [':buildB'])
        loggedOncePerBuild('buildListener.buildFinished failure=null', [':buildC'])
        loggedOncePerBuild('gradle.buildFinished failure=null', [':buildC'])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild("buildListener.buildFinished failure=[org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'., java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]", [':'])
        loggedOncePerBuild("gradle.buildFinished failure=[org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'., java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]", [':'])

        failure.assertHasFailures(4)
        failure.assertHasDescription("Execution failed for task ':buildB:broken'.")
            .assertHasFileName("Build file '${buildB.buildFile}'")
            .assertHasLineNumber(16)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Build file '${buildA.buildFile}'")
            .assertHasLineNumber(17)
        failure.assertHasDescription("build B broken")
            .assertHasFileName("Build file '${buildB.buildFile}'")
            .assertHasLineNumber(13)
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")
            .assertHasLineNumber(9)
    }

    void verifyBuildEvents() {
        events(18) // 3 build * 6 events
        loggedOncePerBuild('buildListener.settingsEvaluated')
        loggedOncePerBuild('buildListener.projectsLoaded')
        loggedOncePerBuild('buildListener.projectsEvaluated')
        loggedOncePerBuild('gradle.taskGraphReady')
        loggedOncePerBuild('buildListener.buildFinished failure=null')
        loggedOncePerBuild('gradle.buildFinished failure=null')
    }

    void loggedOncePerBuild(message, def builds = [':', ':buildB', ':buildC']) {
        builds.each { build ->
            loggedOnce("# $message [$build]")
        }
    }

    void events(int expected) {
        assert result.output.count("# gradle.") + result.output.count("# buildListener.") == expected
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
