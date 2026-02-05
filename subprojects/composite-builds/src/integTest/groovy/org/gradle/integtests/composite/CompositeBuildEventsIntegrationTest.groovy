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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.internal.exceptions.LocationAwareException

class CompositeBuildEventsIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC

    private enum Mode {
        FLOW_ACTION_AND_CALLBACKS(true, true, false, false, true),
        BUILD_FINISHED(false, true, false, true, false),
        BUILD_LISTENER(false, false, true, false, false),

        boolean flowAction
        boolean gradleCallbacks
        boolean buildListener
        boolean gradleBuildFinishedCallback
        boolean ccCompatible

        Mode(boolean flowAction, boolean gradleCallbacks, boolean buildListener, boolean gradleBuildFinishedCallback, boolean ccCompatible) {
            this.flowAction = flowAction
            this.gradleCallbacks = gradleCallbacks
            this.buildListener = buildListener
            this.gradleBuildFinishedCallback = gradleBuildFinishedCallback
            this.ccCompatible = ccCompatible
        }
    }

    def setupInitScript(Mode mode) {
        file("gradle-user-home/init.gradle") << initScriptContent(
                mode.flowAction,
                mode.gradleCallbacks,
                mode.buildListener,
                mode.gradleBuildFinishedCallback
        )
    }

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

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CC compatible: false.*"])
    def "fires build listener events on included builds (mode: #mode, CC compatible: #mode.ccCompatible)"(Mode mode) {
        given:
        setupInitScript(mode)

        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"

        when:
        execute()

        then:
        verifyBuildEvents(mode)

        where:
        mode << [Mode.FLOW_ACTION_AND_CALLBACKS, Mode.BUILD_FINISHED, Mode.BUILD_LISTENER]
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CC compatible: false.*"])
    def "fires build listener events for unused included builds (mode: #mode, CC compatible: #mode.ccCompatible)"(Mode mode) {
        given:
        setupInitScript(mode)

        when:
        execute()

        then:
        def builds = [":", ":buildB", ":buildC"] as String[]
        def shouldBeMissing = ["gradle.taskGraphReady": [":buildB", ":buildC"]]
        verifyBuildEvents(mode, builds, shouldBeMissing)

        where:
        mode << [Mode.FLOW_ACTION_AND_CALLBACKS, Mode.BUILD_FINISHED, Mode.BUILD_LISTENER]
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CC compatible: false.*"])
    def "fires build listener events for included build that provides buildscript and compile dependencies (mode: #mode, CC compatible: #mode.ccCompatible)"(Mode mode) {
        given:
        setupInitScript(mode)

        def pluginBuild = pluginProjectBuild("pluginD")
        applyPlugin(buildA, "pluginD")
        includeBuild pluginBuild

        dependency "org.test:b1:1.0"
        dependency(pluginBuild, "org.test:b2:1.0")

        when:
        execute()

        then:
        def builds = [":", ":buildB", ":buildC", ":pluginD"] as String[]
        def shouldBeMissing = ["gradle.taskGraphReady": [":buildC"]]
        verifyBuildEvents(mode, builds, shouldBeMissing)

        where:
        mode << [Mode.FLOW_ACTION_AND_CALLBACKS, Mode.BUILD_FINISHED, Mode.BUILD_LISTENER]
    }

    @UnsupportedWithConfigurationCache(iterationMatchers = [".*CC compatible: false.*"])
    def "fires build listener events for included builds with additional discovered (compileOnly) dependencies  (mode: #mode, CC compatible: #mode.ccCompatible)"(Mode mode) {
        given:
        setupInitScript(mode)

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
        verifyBuildEvents(mode)

        where:
        mode << [Mode.FLOW_ACTION_AND_CALLBACKS, Mode.BUILD_FINISHED, Mode.BUILD_LISTENER]
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished") // TODO: could we write this test somehow with CC friendly API?
    def "buildFinished for root build is guaranteed to complete after included builds (mode: #mode)"(Mode mode) {
        given:
        setupInitScript(mode)

        dependency "org.test:b1:1.0"
        dependency "org.test:buildC:1.0"
        buildC.buildFile << """
            dependencies {
                compileOnly "org.test:b2:1.0"
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
        def rootBuildFinishedPosition = outputLines.indexOf("# gradleDeprecated.buildFinished failure=null [:]")
        rootBuildFinishedPosition >= 0

        def buildBFinishedPosition = outputLines.indexOf("# gradleDeprecated.buildFinished failure=null [:buildB]")
        buildBFinishedPosition >= 0
        def buildCFinishedPosition = outputLines.indexOf("# gradleDeprecated.buildFinished failure=null [:buildC]")
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
        mode << [Mode.BUILD_FINISHED]
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished") // TODO: could we write this test somehow with CC friendly API?
    def "fires build finished events for all builds when settings script for child build cannot be compiled (mode: #mode)"(Mode mode) {
        given:
        setupInitScript(mode)

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
        loggedOncePerBuild("buildListener.settingsEvaluated", [':'])
        // Root build also receives the failure from its children
        loggedOncePerBuild("buildListener.buildFinished failure=org.gradle.groovy.scripts.ScriptCompilationException: Could not compile settings file '${buildB.settingsFile}'.", [":", ":buildB"])

        failure.assertHasFailures(2)
        failure.assertHasDescription("Could not compile settings file")
            .assertHasFileName("Settings file '${buildB.settingsFile}'")
            .assertHasLineNumber(19)
        failure.assertHasDescription("build A broken")
            .assertHasFileName("Settings file '${buildA.settingsFile}'")
            .assertHasLineNumber(19)

        where:
        mode << [Mode.BUILD_LISTENER]
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished") // TODO: could we write this test somehow with CC friendly API?
    def "fires build finished events for all builds when build script for child build fails (mode: #mode)"() {
        given:
        setupInitScript(mode)

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
        loggedOncePerBuild("${prefix}.buildFinished failure=null", [":buildB"])
        loggedOncePerBuild("${prefix}.buildFinished failure=org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'.", [":buildC"])
        // Root build also receives the failures from its children, including the buildFinished { } failure
        loggedOncePerBuild("${prefix}.buildFinished failure=[org.gradle.api.GradleScriptException: A problem occurred evaluating project ':buildC'., java.lang.RuntimeException: build C broken]", [":"])

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

        where:
        mode                | prefix
        Mode.BUILD_LISTENER | "buildListener"
        Mode.BUILD_FINISHED | "gradleDeprecated"
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished") // TODO: could we write this test somehow with CC friendly API?
    def "fires build finished events for all builds when build finished event for other builds fail (mode: #mode)"() {
        given:
        setupInitScript(mode)

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

        events(eventCount)
        loggedOncePerBuild("${prefix}.buildFinished failure=null", [":buildB", ":buildC"])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild("${prefix}.buildFinished failure=[java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]", [":"])

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

        where:
        mode                | eventCount    | prefix
        Mode.BUILD_LISTENER | 12            | "buildListener"
        Mode.BUILD_FINISHED | 13            | "gradleDeprecated"
    }

    @ToBeFixedForConfigurationCache(because = "buildFinished") // TODO: could we write this test somehow with CC friendly API?
    def "fires build finished events for all builds when other builds fail (mode: #mode)"() {
        given:
        setupInitScript(mode)

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

        events(eventCount)
        loggedOncePerBuild("${prefix}.buildFinished failure=org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'.", [":buildB"])
        loggedOncePerBuild("${prefix}.buildFinished failure=null", [":buildC"])
        // Root build also receives the failures from its children, including the buildFinished { } failures
        loggedOncePerBuild("${prefix}.buildFinished failure=[org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':buildB:broken'., java.lang.RuntimeException: build B broken, java.lang.RuntimeException: build C broken]", [":"])

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

        where:
        mode                | eventCount    | prefix
        Mode.BUILD_LISTENER | 12            | "buildListener"
        Mode.BUILD_FINISHED | 14            | "gradleDeprecated"
    }

    void verifyBuildEvents(Mode mode, String[] builds = [":", ":buildB", ":buildC"], Map<String, String[]> exceptions = [:]) {
        int eventCount = 0
        def expectedMessages = []
        if (mode.flowAction) {
            eventCount += builds.size() - noOfExceptionsFor(exceptions, "flowAction.")
            expectedMessages.add("flowAction.buildFinished failure=null")
        }
        if (mode.buildListener) {
            eventCount += 4 * builds.size() - noOfExceptionsFor(exceptions, "buildListener.")
            expectedMessages.add("buildListener.settingsEvaluated")
            expectedMessages.add("buildListener.projectsLoaded")
            expectedMessages.add("buildListener.projectsEvaluated")
            expectedMessages.add("buildListener.buildFinished failure=null")
        }
        if (mode.gradleCallbacks) {
            eventCount += 4 * builds.size() - noOfExceptionsFor(exceptions, "gradle.")
            expectedMessages.add("gradle.taskGraphReady")
            expectedMessages.add("gradle.settingsEvaluated")
            expectedMessages.add("gradle.projectsLoaded")
            expectedMessages.add("gradle.projectsEvaluated")
        }
        if (mode.gradleBuildFinishedCallback) {
            eventCount += builds.size() - noOfExceptionsFor(exceptions, "gradleDeprecated.buildFinished")
            expectedMessages.add("gradleDeprecated.buildFinished failure=null")
        }

        events(eventCount)
        expectedMessages.forEach { message ->
            loggedOncePerBuild(message, builds, exceptions)
        }
    }

    private static int noOfExceptionsFor(Map<String, String[]> exceptions, String messagePrefix) {
        exceptions.findAll { key, value -> key.startsWith(messagePrefix) }.values().flatten().size()
    }

    void loggedOncePerBuild(message, def builds, Map<String, String[]> exceptions = [:]) {
        builds.each { build ->
            def exceptionBuilds = exceptions[message]
            if (exceptionBuilds != null && exceptionBuilds.contains(build)) {
                notLogged("# $message [$build]")
            } else {
                loggedOnce("# $message [$build]")
            }
        }
    }

    void events(int expected) {
        assert result.output.count("# ") == expected
    }

    void loggedOnce(String message) {
        outputContains(message)
        assert result.output.count(message) == 1
    }

    void notLogged(String message) {
        outputDoesNotContain(message)
    }

    protected void execute() {
        super.execute(buildA, ":resolveArtifacts", ["-I../gradle-user-home/init.gradle"])
    }

    protected void executeAndExpectFailure(String task) {
        super.fails(buildA, task, ["-I../gradle-user-home/init.gradle"])
    }

    private static String initScriptContent(boolean flowAction, boolean gradleCallbacks, boolean buildListener, boolean gradleBuildFinishedCallback) {
        """
            ${if (flowAction) {initScriptFlowAction()} else {""}}

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

            ${if (gradleCallbacks) {"""
                gradle.taskGraph.whenReady {
                    println "# gradle.taskGraphReady [" + gradle.buildPath + "]"
                }
                gradle.settingsEvaluated { settings -> 
                    def buildName = settings.gradle.parent == null ? '' : settings.rootProject.name
                    println "# gradle.settingsEvaluated [:" + buildName + "]"
                }
                gradle.projectsLoaded { gradle ->
                    println "# gradle.projectsLoaded [" + gradle.buildPath + "]" 
                }
                gradle.projectsEvaluated { gradle ->
                    println "# gradle.projectsEvaluated [" + gradle.buildPath + "]"
                }
            """} else {""}}

            ${if (buildListener) {"""
                gradle.addBuildListener(new LoggingBuildListener())
                class LoggingBuildListener extends BuildAdapter {
                    void settingsEvaluated(Settings settings) {
                        def buildName = settings.gradle.parent == null ? '' : settings.rootProject.name
                        println "# buildListener.settingsEvaluated [:" + buildName + "]"
                    }
                    void projectsLoaded(Gradle gradle) {
                        println "# buildListener.projectsLoaded [" + gradle.buildPath + "]"
                    }
                    void projectsEvaluated(Gradle gradle) {
                        println "# buildListener.projectsEvaluated [" + gradle.buildPath + "]"
                    }
                    void buildFinished(BuildResult result) {
                        println "# buildListener.buildFinished failure=" + Util.unpack(result.failure) + " [" + result.gradle.buildPath + "]"
                    }
                }
            """} else {""}}

            ${if (gradleBuildFinishedCallback) {"""
                gradle.buildFinished { result ->
                    println "# gradleDeprecated.buildFinished failure=" + Util.unpack(result.failure) + " [" + gradle.buildPath + "]"
                }
            """} else {""}}
        """
    }

    private static String initScriptFlowAction() {
        """
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
                                            getFlowProviders().getBuildWorkResult().map(result -> "flowAction.buildFinished failure=\${PluginUtil.unpackOptional(result.failure)} [\${buildPath}]")
                                    )
                    );
                }
            }
            
            gradle.beforeSettings { settings ->
                settings.apply plugin: LoggingPlugin
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
            
            class PluginUtil {
                static def unpackOptional(java.util.Optional<Throwable> ot) {
                    if (ot==null || !ot.isPresent()) {
                        return null
                    } else {
                        def t = ot.get()
                        return Util.unpack(t)
                    }
                }
            }
        """
    }

}
