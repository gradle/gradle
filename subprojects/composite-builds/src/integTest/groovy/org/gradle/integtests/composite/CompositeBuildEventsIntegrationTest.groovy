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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile

class CompositeBuildEventsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC

    def setup() {
        file('gradle-user-home/init.gradle') << """
            gradle.buildStarted {
                println 'gradle.buildStarted [' + gradle.identityPath + ']'
            }
            gradle.buildFinished {
                println 'gradle.buildFinished [' + gradle.identityPath + ']'
            }
            gradle.taskGraph.whenReady {
                println 'gradle.taskGraphReady [' + gradle.identityPath + ']'
            }
            gradle.addBuildListener(new LoggingBuildListener())
            class LoggingBuildListener extends BuildAdapter {
                void buildStarted(Gradle gradle) {
                    println 'buildListener.buildStarted [' + gradle.identityPath + ']'
                }
                void settingsEvaluated(Settings settings) {
                    def buildName = settings.gradle.parent == null ? '' : settings.rootProject.name
                    println 'buildListener.settingsEvaluated [:' + buildName + ']'
                }
                void projectsLoaded(Gradle gradle) {
                    println 'buildListener.projectsLoaded [' + gradle.identityPath + ']'
                }
                void projectsEvaluated(Gradle gradle) {
                    println 'buildListener.projectsEvaluated [' + gradle.identityPath + ']'
                }
                void buildFinished(BuildResult result) {
                    println 'buildListener.buildFinished [' + result.gradle.identityPath + ']'
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
        loggedOncePerBuild('buildListener.settingsEvaluated')
        loggedOncePerBuild('buildListener.projectsLoaded')
        loggedOncePerBuild('buildListener.projectsEvaluated')
        loggedOncePerBuild('gradle.taskGraphReady', [':'])
        loggedOncePerBuild('buildListener.buildFinished')
        loggedOncePerBuild('gradle.buildFinished')
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
        loggedOncePerBuild('buildListener.settingsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('buildListener.projectsLoaded', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('buildListener.projectsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('gradle.taskGraphReady', [':', ':buildB', ':pluginD'])
        loggedOncePerBuild('buildListener.buildFinished', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('gradle.buildFinished', [':', ':buildB', ':buildC', ':pluginD'])

        logged("Ignoring listeners of task graph ready event, as this build (:buildB) has already executed work.")
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
        def rootBuildFinishedPosition = outputLines.indexOf("gradle.buildFinished [:]")
        rootBuildFinishedPosition >= 0

        def buildSuccessfulPosition = outputLines.indexOf("BUILD SUCCESSFUL in 0s")
        buildSuccessfulPosition >= 0

        def buildBFinishedPosition = outputLines.indexOf("gradle.buildFinished [:buildB]")
        buildBFinishedPosition >= 0
        def buildCFinishedPosition = outputLines.indexOf("gradle.buildFinished [:buildC]")
        buildCFinishedPosition >= 0

        buildBFinishedPosition < rootBuildFinishedPosition
        buildBFinishedPosition < buildSuccessfulPosition

        buildCFinishedPosition < rootBuildFinishedPosition
        buildCFinishedPosition < buildSuccessfulPosition

        def lastRootBuildTaskPosition = outputLines.indexOf("> Task :resolveArtifacts")
        lastRootBuildTaskPosition >= 0

        def lateIncludedBuildTaskPosition = outputLines.indexOf("> Task :buildB:b2:wait")

        lateIncludedBuildTaskPosition < rootBuildFinishedPosition
    }

    @ToBeFixedForConfigurationCache(because = "build listener")
    def "fires build finished events for all builds when build finished event is other builds fail"() {
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
        fails(buildA, "help")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")
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
        fails(buildA, "broken")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")
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
        loggedOncePerBuild('buildListener.settingsEvaluated')
        loggedOncePerBuild('buildListener.projectsLoaded')
        loggedOncePerBuild('buildListener.projectsEvaluated')
        loggedOncePerBuild('gradle.taskGraphReady')
        loggedOncePerBuild('buildListener.buildFinished')
        loggedOncePerBuild('gradle.buildFinished')

        // buildStarted events should _not_ be logged, since the listeners are added too late
        // If they are logged, it's due to duplicate events fired.
        outputDoesNotContain('gradle.buildStarted')
        outputDoesNotContain('buildListener.buildStarted')
    }

    void loggedOncePerBuild(message, def builds = [':', ':buildB', ':buildC']) {
        builds.each { build ->
            loggedOnce("$message [$build]")
        }
    }

    void loggedOnce(String message) {
        logged(message)
    }

    void logged(String message, int count = 1) {
        outputContains(message)
        assert result.output.count(message) == count
    }

    protected void execute() {
        executer.expectDeprecationWarnings(2) // Due to LoggingBuildListener
        super.execute(buildA, ":resolveArtifacts", ["-I../gradle-user-home/init.gradle"])
    }

}
