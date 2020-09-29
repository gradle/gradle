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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.console.AbstractConsoleGroupedTaskFunctionalTest
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

abstract class AbstractConsoleBuildPhaseFunctionalTest extends AbstractConsoleGroupedTaskFunctionalTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()
    GradleHandle gradle

    def setup() {
        server.start()
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished", skip =  ToBeFixedForConfigurationCache.Skip.LONG_TIMEOUT)
    def "shows progress bar and percent phase completion"() {
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
            gradle.buildFinished {
                ${server.callFromBuild('build-finished')}
            }
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
        assertHasBuildPhase("40% CONFIGURING")
        bBuildScript.releaseAll()

        and:
        bAfterEvaluate.waitForAllPendingCalls()
        assertHasBuildPhase("40% CONFIGURING")
        bAfterEvaluate.releaseAll()

        and:
        taskGraph.waitForAllPendingCalls()
        assertHasBuildPhase("100% CONFIGURING")
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
        gradle.waitForFinish()
    }

    @ToBeFixedForConfigurationCache(because = "build listener", skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP)
    def "shows progress bar and percent phase completion with included build"() {
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
            gradle.buildFinished {
                ${server.callFromBuild('root-build-finished')}
            }
        """
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
        assertHasBuildPhase("75% CONFIGURING")
        rootBuildScript.releaseAll()

        and:
        childTaskGraph.waitForAllPendingCalls()
        assertHasBuildPhase("100% CONFIGURING")
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
        gradle.waitForFinish()
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished", skip =  ToBeFixedForConfigurationCache.Skip.LONG_TIMEOUT)
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
            gradle.buildFinished {
                ${server.callFromBuild('root-build-finished')}
            }
        """
        file("buildSrc/settings.gradle") << """
            include 'a', 'b'
        """
        file("buildSrc/build.gradle") << """
            ${server.callFromBuild('buildsrc-build-script')}
            assemble {
                dependsOn {
                    // call during task graph calculation
                    ${server.callFromBuild('buildsrc-task-graph')}
                    null
                }
                doFirst {
                    ${server.callFromBuild('buildsrc-task')}
                }
            }
            gradle.buildFinished {
                ${server.callFromBuild('buildsrc-build-finished')}
            }
        """

        given:
        def settings = server.expectAndBlock('settings')
        def childBuildScript = server.expectAndBlock('buildsrc-build-script')
        def childTaskGraph = server.expectAndBlock('buildsrc-task-graph')
        def task1 = server.expectAndBlock('buildsrc-task')
        def childBuildFinished = server.expectAndBlock('buildsrc-build-finished')
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
        childBuildFinished.waitForAllPendingCalls()
        assertHasBuildPhase("0% INITIALIZING")
        childBuildFinished.releaseAll()

        and:
        rootBuildScript.waitForAllPendingCalls()
        assertHasBuildPhase("75% CONFIGURING")
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
        gradle.waitForFinish()
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished", skip =  ToBeFixedForConfigurationCache.Skip.LONG_TIMEOUT)
    def "shows progress bar and percent phase completion with artifact transforms"() {
        given:
        settingsFile << """
            include 'lib'
            include 'util'
        """
        buildFile << """
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
                    File output = outputs.registerOutput(input.name + parameters.suffix)
                    output.text = String.valueOf(input.length())
                }
            }

            class FileDoubler extends ArtifactTransform {
                List<File> transform(File input) {
                    ${server.callFromBuild('double-transform')}
                    return [input, input]
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
                    compile jar
                }
            }

            project(':util') {
                dependencies {
                    compile project(':lib')
                    registerTransform {
                        from.attribute(artifactType, "jar")
                        to.attribute(artifactType, "double")
                        artifactTransform(FileDoubler)
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
                    }
                }
            }

            gradle.buildFinished {
                ${server.callFromBuild('build-finished')}
            }
        """
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
        gradle.waitForFinish()
    }

    void assertHasBuildPhase(String message) {
        ConcurrentTestUtil.poll {
            assert gradle.standardOutput =~ regexFor(message)
        }
    }

    String regexFor(String message) {
        /<.*> $message \[[\dms ]+]/
    }
}
