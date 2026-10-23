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

package org.gradle.internal.logging.console

import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.modes.UnsupportedWithIsolatedProjects
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.flow.FlowActionsFixture
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

abstract class AbstractConsoleBuildPhaseFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest implements FlowActionsFixture {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    GradleHandle gradle

    def setup() {
        server.start()
    }

    @UnsupportedWithIsolatedProjects(because = "test verifies cross-project configuration ordering via HTTP-blocking checkpoints; BlockingHttpServer teardown failures escape ToBeFixed's catch")
    def "shows progress bar and percent phase completion"() {
        createDirs("a", "b", "c", "d")
        settingsFile << """
            ${server.callFromBuild('settings')}
            include "a", "b", "c", "d"
        """
        buildFile << """
            ${server.callFromBuild('root-build-script')}
            task hello {
                dependsOn {
                    // call during task graph calculation
                    ${server.callFromBuild('task-graph')}
                    null
                }
                doFirst {
                    ${server.callFromBuild('task1')}
                }
            }
            task hello2 {
                dependsOn hello
                doFirst {
                    ${server.callFromBuild('task2')}
                }
            }
            ${buildFinishedCall('build-finished')}
        """
        file("b/build.gradle") << """
            ${server.callFromBuild('b-build-script')}
            afterEvaluate {
                ${server.callFromBuild('b-after-evaluate')}
            }
        """

        given:
        def settings = server.expectAndBlock('settings')
        def rootBuildScript = server.expectAndBlock('root-build-script')
        def bBuildScript = server.expectAndBlock('b-build-script')
        def bAfterEvaluate = server.expectAndBlock('b-after-evaluate')
        def taskGraph = server.expectAndBlock('task-graph')
        def task1 = server.expectAndBlock('task1')
        def task2 = server.expectAndBlock('task2')
        def buildFinished = server.expectAndBlock('build-finished')
        gradle = executer.withTasks("hello2").start()

        expect:
        settings.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        settings.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("0% CONFIGURING")
        rootBuildScript.releaseAll()

        and:
        bBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase(GradleContextualExecuter.isConfigCache() ? "25% CONFIGURING" : "33% CONFIGURING")
        bBuildScript.releaseAll()

        and:
        bAfterEvaluate.waitForAllPendingCalls()
        assertHasBuildPhase(GradleContextualExecuter.isConfigCache() ? "25% CONFIGURING" : "33% CONFIGURING")
        bAfterEvaluate.releaseAll()

        and:
        taskGraph.waitForAllPendingCalls()
        assertHasBuildPhase(GradleContextualExecuter.isConfigCache() ? "62% CONFIGURING" : "83% CONFIGURING")
        taskGraph.releaseAll()

        and:
        task1.waitForAllPendingCalls()
        assertHasBuildPhase("0% EXECUTING")
        task1.releaseAll()

        and:
        task2.waitForAllPendingCalls()
        assertHasBuildPhase("50% EXECUTING")
        task2.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("100% EXECUTING")
        buildFinished.releaseAll()

        and:
        waitForFinish()
    }

    def "shows progress bar and percent phase completion with included build"() {
        createDirs("child")
        settingsFile << """
            ${server.callFromBuild('settings')}
            includeBuild "child"
        """
        buildFile << """
            ${server.callFromBuild('root-build-script')}
            task hello2 {
                dependsOn gradle.includedBuild("child").task(":hello")
                doFirst {
                    ${server.callFromBuild('task2')}
                }
            }
            ${buildFinishedCall('root-build-finished')}
        """
        createDirs("child/a", "child/b")
        file("child/settings.gradle") << """
            include 'a', 'b'
        """
        file("child/build.gradle") << """
            ${server.callFromBuild('child-build-script')}
            task hello {
                dependsOn {
                    // call during task graph calculation
                    ${server.callFromBuild('child-task-graph')}
                    null
                }
                doFirst {
                    ${server.callFromBuild('task1')}
                }
            }
        """

        given:
        def settings = server.expectAndBlock('settings')
        def childBuildScript = server.expectAndBlock('child-build-script')
        def rootBuildScript = server.expectAndBlock('root-build-script')
        def childTaskGraph = server.expectAndBlock('child-task-graph')
        def task1 = server.expectAndBlock('task1')
        def task2 = server.expectAndBlock('task2')
        def rootBuildFinished = server.expectAndBlock('root-build-finished')
        gradle = executer.withTasks("hello2").start()

        expect:
        settings.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        settings.releaseAll()

        and:
        childBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        childBuildScript.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase(GradleContextualExecuter.isConfigCache() ? "42% CONFIGURING" : "60% CONFIGURING")
        rootBuildScript.releaseAll()

        and:
        childTaskGraph.waitForAllPendingCalls()
        assertHasBuildPhase(GradleContextualExecuter.isConfigCache() ? "57% CONFIGURING" : "80% CONFIGURING")
        childTaskGraph.releaseAll()

        and:
        task1.waitForAllPendingCalls()
        assertHasBuildPhase("0% EXECUTING")
        task1.releaseAll()

        and:
        task2.waitForAllPendingCalls()
        assertHasBuildPhase("50% EXECUTING")
        task2.releaseAll()

        and:
        rootBuildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("100% EXECUTING")
        rootBuildFinished.releaseAll()

        and:
        waitForFinish()
    }

    def "shows progress bar and percent phase completion with buildSrc build"() {
        settingsFile << """
            ${server.callFromBuild('settings')}
        """
        buildFile << """
            ${server.callFromBuild('root-build-script')}
            task hello {
                doFirst {
                    ${server.callFromBuild('task2')}
                }
            }
            ${buildFinishedCall('root-build-finished')}
        """
        createDirs("buildSrc", "buildSrc/a", "buildSrc/b")
        file("buildSrc/settings.gradle") << """
            include 'a', 'b'
        """
        file("buildSrc/build.gradle") << """
            ${server.callFromBuild('buildsrc-build-script')}
            jar {
                dependsOn {
                    // call during task graph calculation
                    ${server.callFromBuild('buildsrc-task-graph')}
                    null
                }
                doFirst {
                    ${server.callFromBuild('buildsrc-task')}
                }
            }
        """

        given:
        def settings = server.expectAndBlock('settings')
        def childBuildScript = server.expectAndBlock('buildsrc-build-script')
        def childTaskGraph = server.expectAndBlock('buildsrc-task-graph')
        def task1 = server.expectAndBlock('buildsrc-task')
        def rootBuildScript = server.expectAndBlock('root-build-script')
        def task2 = server.expectAndBlock('task2')
        def rootBuildFinished = server.expectAndBlock('root-build-finished')
        gradle = executer.withTasks("hello").start()

        expect:
        settings.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        settings.releaseAll()

        and:
        childBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        childBuildScript.releaseAll()

        and:
        childTaskGraph.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        childTaskGraph.releaseAll()

        and:
        task1.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        task1.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase(GradleContextualExecuter.isConfigCache() ? "50% CONFIGURING" : "66% CONFIGURING")
        rootBuildScript.releaseAll()

        and:
        task2.waitForAllPendingCalls()
        assertHasBuildPhase("0% EXECUTING")
        task2.releaseAll()

        and:
        rootBuildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("100% EXECUTING")
        rootBuildFinished.releaseAll()

        and:
        waitForFinish()
    }

    def "configuring phase completes before executing when #scenario"() {
        blockWhenRunningMainTasksStarts('run-main-tasks')
        buildFile << """
            abstract class DegradingTask extends DefaultTask {
                @Inject
                abstract org.gradle.api.internal.ConfigurationCacheDegradationController getDegradationController()
            }
            tasks.register("hello", DegradingTask) { task ->
                $taskConfiguration
                doFirst {
                    ${server.callFromBuild('task')}
                }
            }
        """

        given:
        def runMainTasks = server.expectAndBlock('run-main-tasks')
        def task = server.expectAndBlock('task')
        gradle = executer.withTasks("hello").start()

        expect:
        runMainTasks.waitForAllPendingCalls()
        assertHasBuildPhase("100% CONFIGURING")
        runMainTasks.releaseAll()

        and:
        task.waitForAllPendingCalls()
        assertHasBuildPhase("0% EXECUTING")
        task.releaseAll()

        and:
        waitForFinish()

        where:
        scenario                            | taskConfiguration
        "the configuration is stored"       | ""
        "the stored entry is discarded"     | "notCompatibleWithConfigurationCache('testing')"
        "the store degrades gracefully"     | "degradationController.requireConfigurationCacheDegradation(task, provider { 'testing' })"
    }

    /**
     * Blocks the build when the root build starts running its main tasks, before the console switches to the executing phase.
     * This makes the final state of the configuring phase observable.
     */
    void blockWhenRunningMainTasksStarts(String resource) {
        initScriptFile """
            import org.gradle.internal.operations.BuildOperationCategory
            import org.gradle.internal.operations.BuildOperationDescriptor
            import org.gradle.internal.operations.BuildOperationListener
            import org.gradle.internal.operations.BuildOperationListenerManager
            import org.gradle.internal.operations.OperationFinishEvent
            import org.gradle.internal.operations.OperationIdentifier
            import org.gradle.internal.operations.OperationProgressEvent
            import org.gradle.internal.operations.OperationStartEvent

            def manager = gradle.services.get(BuildOperationListenerManager)
            manager.addListener(new BlockingListener(manager: manager))

            class BlockingListener implements BuildOperationListener {
                BuildOperationListenerManager manager

                void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
                    // Listeners are notified before the console receives the start of the operation
                    if (buildOperation.metadata == BuildOperationCategory.RUN_MAIN_TASKS) {
                        manager.removeListener(this)
                        ${server.callFromBuild(resource)}
                    }
                }

                void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {}

                void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {}
            }
        """
        executer.usingInitScript(initScriptFile)
    }

    @ToBeFixedForIsolatedProjects(because = "multi-project tests via BlockingHttpServer/parallel fail under IP")
    def "shows progress bar and percent phase completion with artifact transforms"() {
        given:
        setupTransformBuild()
        def jar = server.expectAndBlock('jar')
        def doubleTransform = server.expectAndBlock('double-transform')
        def sizeTransform = server.expectAndBlock('size-transform')
        def resolveTask = server.expectAndBlock('resolve-task')
        def buildFinished = server.expectAndBlock('build-finished')

        when:
        gradle = executer.withTasks(":util:resolve").start()

        then:
        jar.waitForAllPendingCalls()
        assertHasBuildPhase("0% EXECUTING")
        jar.releaseAll()

        and:
        doubleTransform.waitForAllPendingCalls()
        assertHasBuildPhase("25% EXECUTING")
        doubleTransform.releaseAll()

        and:
        sizeTransform.waitForAllPendingCalls()
        assertHasBuildPhase("50% EXECUTING")
        sizeTransform.releaseAll()

        and:
        resolveTask.waitForAllPendingCalls()
        assertHasBuildPhase("75% EXECUTING")
        resolveTask.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("100% EXECUTING")
        buildFinished.releaseAll()

        and:
        waitForFinish()
    }

    @Issue("https://github.com/gradle/gradle/issues/24370")
    @ToBeFixedForIsolatedProjects(because = "multi-project tests via BlockingHttpServer/parallel fail under IP")
    def "shows progress bar and percent phase completion with non-planned planned artifact transforms"() {
        given:
        setupTransformBuild()
        def jar = server.expectAndBlock('jar')
        def resolveTask = server.expectAndBlock('resolve-task')
        def doubleTransform = server.expectAndBlock('double-transform')
        def sizeTransform = server.expectAndBlock('size-transform')
        def buildFinished = server.expectAndBlock('build-finished')

        when:
        gradle = executer.withTasks(":util:resolveWithoutDependencies").start()

        then:
        jar.waitForAllPendingCalls()
        assertHasBuildPhase("0% EXECUTING")
        jar.releaseAll()

        and:
        resolveTask.waitForAllPendingCalls()
        assertHasBuildPhase("50% EXECUTING")
        resolveTask.releaseAll()

        and:
        doubleTransform.waitForAllPendingCalls()
        assertHasBuildPhase("50% EXECUTING")
        doubleTransform.releaseAll()

        and:
        sizeTransform.waitForAllPendingCalls()
        assertHasBuildPhase("50% EXECUTING")
        sizeTransform.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("100% EXECUTING")
        buildFinished.releaseAll()

        when:
        result = gradle.waitForFinish()
        then:
        outputDoesNotContain("More progress was logged than there should be")
    }

    def setupTransformBuild() {
        createDirs("lib", "util")
        settingsFile << """
            include 'lib'
            include 'util'
        """
        buildFile << """
            import org.gradle.api.artifacts.transform.TransformParameters

            def usage = Attribute.of('usage', String)
            def artifactType = Attribute.of('artifactType', String)

            abstract class FileSizer implements TransformAction<Parameters> {
                interface Parameters extends TransformParameters {
                    @Input
                    String getSuffix()
                    void setSuffix(String suffix)
                }

                @InputArtifactDependencies
                abstract FileCollection getDependencies()
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    def input = inputArtifact.get().asFile
                    ${server.callFromBuild('size-transform')}
                    File output = outputs.file(input.name + parameters.suffix)
                    output.text = String.valueOf(input.length())
                }
            }

            abstract class FileDoubler implements TransformAction<TransformParameters.None> {
                @InputArtifact
                abstract Provider<FileSystemLocation> getInputArtifact()

                void transform(TransformOutputs outputs) {
                    ${server.callFromBuild('double-transform')}
                    outputs.file(inputArtifact)
                    outputs.file(inputArtifact)
                }
            }

            allprojects {
                dependencies {
                    attributesSchema {
                        attribute(usage)
                    }
                }
                configurations {
                    compile {
                        attributes.attribute usage, 'api'
                    }
                }
            }

            project(':lib') {
                apply plugin: 'base'
                task jar(type: Jar) {
                    destinationDirectory = buildDir
                    archiveFileName = 'lib.jar'
                    doLast {
                        ${server.callFromBuild('jar')}
                    }
                }
                artifacts {
                    compile tasks.jar
                }
            }

            project(':util') {
                dependencies {
                    compile project(':lib')
                    registerTransform(FileDoubler) {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "double")
                    }
                    registerTransform(FileSizer) {
                        from.attribute(artifactType, "double")
                        to.attribute(artifactType, "size")
                        parameters {
                            suffix = ".txt"
                        }
                    }
                }
                task resolve {
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts

                    inputs.files size.artifactFiles

                    doLast {
                        ${server.callFromBuild('resolve-task')}
                        size.artifactFiles.files.each { println it }
                    }
                }
                task resolveWithoutDependencies {
                    def size = configurations.compile.incoming.artifactView {
                        attributes { it.attribute(artifactType, 'size') }
                    }.artifacts

                    dependsOn(":lib:jar")

                    doLast {
                        ${server.callFromBuild('resolve-task')}
                        size.artifactFiles.files.each { println it }
                    }
                }
            }

            ${buildFinishedCall('build-finished')}
        """
    }

    void assertHasBuildPhase(String message) {
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput =~ regexFor(message)
        }
    }

    String regexFor(String message) {
        /$ProgressBar.UNICODE_PROGRESS_BAR_PREFIX.*$ProgressBar.UNICODE_PROGRESS_BAR_SUFFIX $message \[[\dms ]+]/
    }

    void waitForFinish() {
        result = gradle.waitForFinish()
        outputDoesNotContain("More progress was logged than there should be")
    }

    String buildFinishedCall(String name) {
        buildFinishedFlowAction(server.callFromBuild(name))
    }
}
