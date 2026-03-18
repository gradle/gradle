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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.flow.FlowActionsFixture
import org.gradle.internal.exceptions.LocationAwareException

class CompositeBuildEventsIntegrationTest extends AbstractCompositeBuildIntegrationTest implements FlowActionsFixture {
    BuildTestFile buildB
    BuildTestFile buildC

    def setup() {
        buildA.buildFile << """
            task resolveArtifacts(type: Copy) {
                from configurations.compileClasspath
                into "libs"
            }
        """

        buildB = multiProjectBuild("buildB", ["b1", "b2"]) {
            buildFile << """
                allprojects {
                    apply plugin: "java"
                }
            """
        }
        includedBuilds << buildB

        buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: "java"
            """
        }
        includedBuilds << buildC
    }

    def setupInitScript(BuildFinishCallbackType buildFinishCallbackType) {
        file("gradle-user-home/init.gradle") << """
            class Util {
                static def unpack(Object o) {
                    if (o == null) {
                        return null
                    }
                    if (o instanceof ${Throwable.name}) {
                        ${Throwable.name} t = (${Throwable.name}) o
                        if (t instanceof ${LocationAwareException.name}) {
                            return unpack(t.cause)
                        } else if (t instanceof ${MultipleBuildFailures.name}) {
                            return t.causes.collect { unpack(it) }
                        } else {
                            return t
                        }
                    } else if (o instanceof ${Optional.name}) {
                        ${Optional.name} ot = (${Optional.name}) o
                        if (ot==null || !ot.isPresent()) {
                           return null
                        } else {
                            def t = ot.get()
                            return Util.unpack(t)
                        }
                    } else {
                        throw new RuntimeException("Unsupported type " + o.class.getName())
                    }
                }
            }

            gradle.taskGraph.whenReady {
                println "# taskGraphReady [" + gradle.buildPath + "]"
            }
            gradle.settingsEvaluated { settings -> 
                def buildName = settings.gradle.parent == null ? '' : settings.rootProject.name
                println "# settingsEvaluated [:" + buildName + "]"
            }
            gradle.projectsLoaded { gradle ->
                println "# projectsLoaded [" + gradle.buildPath + "]" 
            }
            gradle.projectsEvaluated { gradle ->
                println "# projectsEvaluated [" + gradle.buildPath + "]"
            }

            ${buildFinishCallback(buildFinishCallbackType, """
                println "# buildFinished failure=" + Util.unpack(result.failure) + " [" + buildPath + "]"
            """)}
        """
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build listener events on included builds"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"

        when:
        execute()

        then:
        verifyBuildEvents()

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build listener events for unused included builds"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        when:
        execute()

        then:
        events(13)
        loggedOncePerBuild('settingsEvaluated')
        loggedOncePerBuild('projectsLoaded')
        loggedOncePerBuild('projectsEvaluated')
        loggedOncePerBuild('taskGraphReady', [':'])
        loggedOncePerBuild('buildFinished failure=null')

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build listener events for included build that provides buildscript and compile dependencies"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        def pluginBuild = pluginProjectBuild("pluginD")
        applyPlugin(buildA, "pluginD")
        includeBuild pluginBuild

        dependency "org.test:b1:1.0"
        dependency(pluginBuild, "org.test:b2:1.0")

        when:
        execute()

        then:
        events(19)
        loggedOncePerBuild('settingsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('projectsLoaded', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('projectsEvaluated', [':', ':buildB', ':buildC', ':pluginD'])
        loggedOncePerBuild('taskGraphReady', [':', ':buildB', ':pluginD'])
        loggedOncePerBuild('buildFinished failure=null', [':', ':buildB', ':buildC', ':pluginD'])

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build listener events for included builds with additional discovered (compileOnly) dependencies"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        // BuildB will be initially evaluated with a single dependency on "b1".
        // Dependency on "b2" is discovered while constructing the task graph for "buildC".
        dependency "org.test:b1:1.0"
        dependency "org.test:buildC:1.0"
        buildC.buildFile << """
            dependencies {
                compileOnly "org.test:b2:1.0"
            }
        """

        when:
        execute()

        then:
        verifyBuildEvents()

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "buildFinished for root build is guaranteed to complete after included builds"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        dependency "org.test:b1:1.0"
        dependency "org.test:buildC:1.0"
        buildC.buildFile << """
            dependencies {
                compileOnly "org.test:b2:1.0"
            }

            ${buildFinishCallback(buildFinishedCallbackType, """
                sleep 500
            """)}
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
        def rootBuildFinishedPosition = outputLines.indexOf("# buildFinished failure=null [:]" as String)
        rootBuildFinishedPosition >= 0

        def buildBFinishedPosition = outputLines.indexOf("# buildFinished failure=null [:buildB]" as String)
        buildBFinishedPosition >= 0
        def buildCFinishedPosition = outputLines.indexOf("# buildFinished failure=null [:buildC]" as String)
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

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build finished events for all builds when settings script for child build cannot be compiled"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        buildA.settingsFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build A finished"
                throw new RuntimeException("build A broken")
            """)}
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
        // Root build also receives the failure from its children
        loggedOncePerBuild("buildFinished failure=org.gradle.groovy.scripts.ScriptCompilationException: Could not compile settings file '${buildB.settingsFile}'.", [":", ":buildB"])

        failure.assertHasFailures(2)
        failure.assertHasDescription("Could not compile settings file")
            .assertHasFileName("Settings file '${buildB.settingsFile}'")

        failure.assertHasDescription("build A broken")
            .assertHasFileName("Settings file '${buildA.settingsFile}'")

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build finished events for all builds when build script for child build fails"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        disableProblemsApiCheck()
        buildA.settingsFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build A finished"
                throw new RuntimeException("build A broken")
            """)}
        """
        buildB.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build B finished"
            """)}
        """
        buildC.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build C finished"
                throw new RuntimeException("build C broken")
            """)}

            throw new RuntimeException("failed in build C")
        """

        when:
        executeAndExpectFailure("help")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")

        events(10)
        loggedOncePerBuild("buildFinished failure=null", [":buildB"])
        loggedOncePerBuild("buildFinished failure=org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'.", [":buildC"])
        // Root build also receives the failures from its children, including the buildFinished { } failure
        loggedOncePerBuild("buildFinished failure=[org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'., java.lang.RuntimeException: build C broken]", [":"])

        failure.assertHasFailures(3)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Settings file '${buildA.settingsFile}'")
        failure.assertHasCause("failed in build C")
            .assertHasFileName("Build file '${buildC.buildFile}'")
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build finished events for all builds when build finished event for other builds fail"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        disableProblemsApiCheck()
        buildA.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build A finished"
                throw new RuntimeException("build A broken")
            """)}
        """
        buildB.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build B finished"
                throw new RuntimeException("build B broken")
            """)}
        """
        buildC.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build C finished"
                throw new RuntimeException("build C broken")
            """)}
        """

        when:
        executeAndExpectFailure("help")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")

        events(13)
        loggedOncePerBuild("buildFinished failure=null", [":buildB", ":buildC"])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild("buildFinished failure=[java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]", [":"])

        failure.assertHasFailures(3)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Build file '${buildA.buildFile}'")
        failure.assertHasDescription("build B broken")
            .assertHasFileName("Build file '${buildB.buildFile}'")
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes() 
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CallbackType: BUILD_.*"], because = "gradle.buildFinished or BuildListener")
    def "fires build finished events for all builds when other builds fail"() {
        given:
        setupInitScript(buildFinishedCallbackType)

        disableProblemsApiCheck()
        buildA.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build A finished"
                throw new RuntimeException("build A broken")
            """)}
            task broken {
                dependsOn gradle.includedBuild("buildB").task(":broken")
            }
        """
        buildB.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build B finished"
                throw new RuntimeException("build B broken")
            """)}
            task broken {
                doLast { throw new RuntimeException("task broken") }
            }
        """
        buildC.buildFile << """
            ${buildFinishCallback(buildFinishedCallbackType, """
                println "build C finished"
                throw new RuntimeException("build C broken")
            """)}
        """

        when:
        executeAndExpectFailure("broken")

        then:
        outputContains("build A finished")
        outputContains("build B finished")
        outputContains("build C finished")

        events(14)
        loggedOncePerBuild("buildFinished failure=org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'.", [":buildB"])
        loggedOncePerBuild("buildFinished failure=null", [":buildC"])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild("buildFinished failure=[org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'., java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]", [":"])

        failure.assertHasFailures(4)
        failure.assertHasDescription("Execution failed for task ':buildB:broken'.")
            .assertHasFileName("Build file '${buildB.buildFile}'")
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Build file '${buildA.buildFile}'")
        failure.assertHasDescription("build B broken")
            .assertHasFileName("Build file '${buildB.buildFile}'")
        failure.assertHasDescription("build C broken")
            .assertHasFileName("Build file '${buildC.buildFile}'")

        where:
        buildFinishedCallbackType << buildFinishCallbackTypes()
    }

    void verifyBuildEvents() {
        events(15) // 3 build * 5 events

        loggedOncePerBuild('buildFinished failure=null')
        loggedOncePerBuild('taskGraphReady')
        loggedOncePerBuild('settingsEvaluated')
        loggedOncePerBuild('projectsLoaded')
        loggedOncePerBuild('projectsEvaluated')
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
