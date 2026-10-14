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
    def "shows phase and counts"() {
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
        assertHasBuildPhase("INITIALIZING")
        settings.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 1 … 4")
        rootBuildScript.releaseAll()

        and:
        bBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 1 … 2")
        bBuildScript.releaseAll()

        and:
        bAfterEvaluate.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 1 … 2")
        bAfterEvaluate.releaseAll()

        and:
        taskGraph.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 0 … 0")
        taskGraph.releaseAll()

        and:
        task1.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 1")
        task1.releaseAll()

        and:
        task2.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 0")
        task2.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 0 … 0")
        buildFinished.releaseAll()

        and:
        waitForFinish()
    }

    def "shows phase and counts with included build"() {
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
        assertHasBuildPhase("INITIALIZING")
        settings.releaseAll()

        and:
        childBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 1 … 3")
        childBuildScript.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 1 … 0")
        rootBuildScript.releaseAll()

        and:
        childTaskGraph.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 0 … 0")
        childTaskGraph.releaseAll()

        and:
        task1.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 1")
        task1.releaseAll()

        and:
        task2.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 0")
        task2.releaseAll()

        and:
        rootBuildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 0 … 0")
        rootBuildFinished.releaseAll()

        and:
        waitForFinish()
    }

    def "shows phase and counts with buildSrc build"() {
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
        assertHasBuildPhase("INITIALIZING")
        settings.releaseAll()

        and:
        childBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 1 … 3")
        childBuildScript.releaseAll()

        and:
        childTaskGraph.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 0 … 1")
        childTaskGraph.releaseAll()

        and:
        task1.waitForAllPendingCalls()
        // jar runs, and the root project is not configured yet
        assertHasBuildPhase("CONFIGURING ▶ 1 … 1")
        task1.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("CONFIGURING ▶ 1 … 0")
        rootBuildScript.releaseAll()

        and:
        task2.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 0")
        task2.releaseAll()

        and:
        rootBuildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 0 … 0")
        rootBuildFinished.releaseAll()

        and:
        waitForFinish()
    }

    def "shows initialization progress for included plugin builds"() {
        settingsFile << """
            pluginManagement {
                includeBuild('plugins')
            }
            plugins {
                id('my.settings-plugin')
            }
            ${server.callFromBuild('settings')}
        """
        buildFile << """
            task hello
            ${buildFinishedCall('build-finished')}
        """
        file("plugins/settings.gradle") << """
            rootProject.name = 'plugins'
        """
        file("plugins/build.gradle") << """
            plugins {
                id('groovy-gradle-plugin')
            }
        """
        file("plugins/src/main/groovy/my.settings-plugin.settings.gradle") << ""

        given:
        def settings = server.expectAndBlock('settings')
        def buildFinished = server.expectAndBlock('build-finished')
        gradle = executer.withTasks("hello").start()

        expect:
        settings.waitForAllPendingCalls()
        // The plugin build is configured and built before the settings script body runs
        assertHasBuildPhase("INITIALIZING ▶ 0 … 0")
        settings.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 0 … 0")
        buildFinished.releaseAll()

        and:
        waitForFinish()
    }

    def "does not count projects that are never configured with configure-on-demand"() {
        createDirs("a", "b")
        settingsFile << """
            include "a", "b"
        """
        buildFile << """
            ${buildFinishedCall('build-finished')}
        """
        file("a/build.gradle") << """
            task hello
        """

        given:
        def buildFinished = server.expectAndBlock('build-finished')
        gradle = executer.withArgument("--configure-on-demand").withTasks(":a:hello").start()

        expect:
        buildFinished.waitForAllPendingCalls()
        // Project b is never configured, so it is not counted
        assertHasBuildPhase("EXECUTING ▶ 0 … 0")
        buildFinished.releaseAll()

        and:
        waitForFinish()
    }

    @ToBeFixedForIsolatedProjects(because = "multi-project tests via BlockingHttpServer/parallel fail under IP")
    def "shows phase and counts with artifact transforms"() {
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
        assertHasBuildPhase("EXECUTING ▶ 1 … 3")
        jar.releaseAll()

        and:
        doubleTransform.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 2")
        doubleTransform.releaseAll()

        and:
        sizeTransform.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 1")
        sizeTransform.releaseAll()

        and:
        resolveTask.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 0")
        resolveTask.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 0 … 0")
        buildFinished.releaseAll()

        and:
        waitForFinish()
    }

    @Issue("https://github.com/gradle/gradle/issues/24370")
    @ToBeFixedForIsolatedProjects(because = "multi-project tests via BlockingHttpServer/parallel fail under IP")
    def "shows phase and counts with non-planned planned artifact transforms"() {
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
        assertHasBuildPhase("EXECUTING ▶ 1 … 1")
        jar.releaseAll()

        and:
        resolveTask.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 0")
        resolveTask.releaseAll()

        and:
        doubleTransform.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 0")
        doubleTransform.releaseAll()

        and:
        sizeTransform.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 1 … 0")
        sizeTransform.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("EXECUTING ▶ 0 … 0")
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

    def "shows progress bar and percent phase completion with work items"() {
        buildFile << """
            interface BlockingParams extends WorkParameters {
                Property<String> getItemName()
            }

            abstract class BlockingAction implements WorkAction<BlockingParams> {
                @Override
                void execute() {
                    ${server.callFromBuildUsingExpression('parameters.itemName.get()')}
                }
            }

            abstract class SubmittingTask extends DefaultTask {
                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void submit() {
                    def queue = workerExecutor.noIsolation()
                    ['item1', 'item2', 'item3'].each { name ->
                        queue.submit(BlockingAction) { it.itemName = name }
                    }
                }
            }

            task work(type: SubmittingTask)
            task hello2 {
                dependsOn work
                doFirst {
                    ${server.callFromBuild('task2')}
                }
            }
            ${buildFinishedCall('build-finished')}
        """

        given:
        def items = server.expectConcurrentAndBlock('item1', 'item2', 'item3')
        def task2 = server.expectAndBlock('task2')
        def buildFinished = server.expectAndBlock('build-finished')
        gradle = executer.withTasks("hello2").withArgument("--max-workers=4").start()

        expect:
        items.waitForAllPendingCalls()
        assertHasBuildPhase("0% EXECUTING")
        items.release('item1')

        and:
        assertHasBuildPhase("20% EXECUTING")
        items.release('item2')

        and:
        assertHasBuildPhase("40% EXECUTING")
        items.release('item3')

        and:
        task2.waitForAllPendingCalls()
        assertHasBuildPhase("80% EXECUTING")
        task2.releaseAll()

        and:
        buildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("100% EXECUTING")
        buildFinished.releaseAll()

        and:
        waitForFinish()
    }

    void assertHasBuildPhase(String message) {
        int drawnBefore = RenderedBuildStatus.all(gradle.standardOutput).size()
        ConcurrentTestUtil.poll {
            def statuses = RenderedBuildStatus.all(gradle.standardOutput)
            // Wait for the bar to be drawn again, so it shows everything that happened before the build blocked
            assert statuses.size() > drawnBefore
            assert statuses.last() == message
        }
    }

    void waitForFinish() {
        result = gradle.waitForFinish()
        outputDoesNotContain("More progress was logged than there should be")
    }

    String buildFinishedCall(String name) {
        buildFinishedFlowAction(server.callFromBuild(name))
    }
}
