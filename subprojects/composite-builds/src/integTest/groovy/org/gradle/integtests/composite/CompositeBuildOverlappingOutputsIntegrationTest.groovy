/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

/**
 * Tests that the execution node access hierarchies are shared by all builds in a build tree,
 * so that tasks with overlapping outputs or destroyables in different builds are
 * subject to the same mutation ordering constraints as tasks within a single build.
 */
class CompositeBuildOverlappingOutputsIntegrationTest extends AbstractCompositeBuildIntegrationTest {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def setup() {
        server.start()
    }

    def "tasks with overlapping outputs in different builds do not run in parallel"() {
        given:
        def sharedDir = normaliseFileSeparators(file("shared-output").absolutePath)

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                tasks.register("includedPing") {
                    outputs.dir(file("$sharedDir"))
                    doLast {
                        ${server.callFromBuild("includedPing")}
                    }
                }
            """
        }
        includeBuild(buildB)

        buildA.buildFile << """
            tasks.register("rootPing") {
                outputs.file(file("$sharedDir/file.txt"))
                doLast {
                    ${server.callFromBuild("rootPing")}
                }
            }
        """

        // With concurrent == 1, each call is held until explicitly released, and a second call
        // arriving while another is held fails immediately as an unexpected request
        def handle = server.expectConcurrentAndBlock(1, "rootPing", "includedPing")

        when:
        def build = executer.inDirectory(buildA)
            .withTasks(":buildB:includedPing", ":rootPing")
            .start()

        then:
        // One of the two tasks is now frozen mid-action, holding its claim on the shared output location
        handle.waitForAllPendingCalls()
        // This is the detection window. If the mutual exclusion of overlapping outputs across builds
        // were broken, the other task would start while the first is frozen, its call would arrive at
        // the server, and the test would fail immediately with an unexpected request error.
        Thread.sleep(500)
        handle.release(1)
        // Now the second task is allowed to run and make its call
        handle.waitForAllPendingCalls()
        handle.release(1)
        build.waitForFinish()
    }

    def "task consuming the overlapping output location of a task in another build without a dependency emits a deprecation warning"() {
        given:
        enableProblemsApiCheck()
        def sharedDir = normaliseFileSeparators(file("shared-output").absolutePath)

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                tasks.register("producer") {
                    outputs.dir(file("$sharedDir"))
                    def outputFile = file("$sharedDir/produced.txt")
                    doLast {
                        outputFile.text = "produced"
                        ${server.callFromBuild("producer")}
                    }
                }
            """
        }
        includedBuilds << buildB

        buildA.buildFile << """
            tasks.register("gate") {
                doLast {
                    ${server.callFromBuild("gate")}
                }
            }
            tasks.register("consumer") {
                dependsOn("gate")
                inputs.dir(file("$sharedDir"))
                def outputFile = file("consumer-out.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        // Ensure the producer is executing (and thus its outputs are recorded in the shared hierarchy)
        // before the gate task completes and the consumer starts, without introducing a dependency
        // between the producer and the consumer
        server.expectConcurrent("producer", "gate")

        executer.expectDocumentedDeprecationWarning(
            "Producing a file in one build and consuming it in another build without declaring an explicit dependency has been deprecated. " +
                "This will fail with an error in Gradle 10. " +
                "Gradle detected a problem with the following location: '${file("shared-output").absolutePath}'. " +
                "Task ':consumer' uses this output of task ':buildB:producer' without declaring an explicit or implicit dependency. " +
                "This can lead to incorrect results being produced, depending on what order the tasks are executed. " +
                "For more information, please refer to https://docs.gradle.org/current/userguide/validation_problems.html#implicit_dependency in the Gradle documentation.")

        when:
        execute(buildA, [":buildB:producer", ":consumer"] as String[])

        then:
        executed(":buildB:producer", ":consumer")
        buildA.file("consumer-out.txt").text == "consumed"
        verifyAll(receivedProblem(0)) {
            fqid == 'deprecation:implicit-dependency-between-tasks-in-different-builds'
            definition.id.displayName == 'Implicit dependency between tasks in different builds'
            contextualLabel == "Producing a file in one build and consuming it in another build without declaring an explicit dependency has been deprecated."
            details == 'This will fail with an error in Gradle 10.'
            solutions == [
                "Gradle detected a problem with the following location: '${file("shared-output").absolutePath}'. " +
                    "Task ':consumer' uses this output of task ':buildB:producer' without declaring an explicit or implicit dependency. " +
                    "This can lead to incorrect results being produced, depending on what order the tasks are executed."
            ]
        }
    }

    def "task consuming the overlapping output location of a task in another build with a declared dependency does not emit a deprecation warning"() {
        given:
        def sharedDir = normaliseFileSeparators(file("shared-output").absolutePath)

        def buildB = singleProjectBuild("buildB") {
            buildFile << """
                tasks.register("producer") {
                    outputs.dir(file("$sharedDir"))
                    def outputFile = file("$sharedDir/produced.txt")
                    doLast {
                        outputFile.text = "produced"
                    }
                }
            """
        }
        includedBuilds << buildB

        buildA.buildFile << """
            tasks.register("consumer") {
                dependsOn(gradle.includedBuild("buildB").task(":producer"))
                inputs.dir(file("$sharedDir"))
                def outputFile = file("consumer-out.txt")
                outputs.file(outputFile)
                doLast {
                    outputFile.text = "consumed"
                }
            }
        """

        expect:
        execute(buildA, "consumer")
        executed(":buildB:producer", ":consumer")
        buildA.file("consumer-out.txt").text == "consumed"
    }

}
