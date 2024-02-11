/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

class WorkerDaemonLoggingIntegrationTest extends AbstractDaemonWorkerExecutorIntegrationSpec {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def workActionThatProducesLotsOfOutput = fixture.getWorkActionThatCreatesFiles("LotsOfOutputWorkAction")

    def setup() {
        workActionThatProducesLotsOfOutput.with {
            action += """
                1000.times {
                    println "foo!"
                }
            """
        }

        fixture.withWorkActionClassInBuildScript()
        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${workActionThatProducesLotsOfOutput.name}.class
            }
        """
    }

    @Issue("https://github.com/gradle/gradle/issues/10122")
    def "can log many messages from action in a worker daemon"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        workActionThatProducesLotsOfOutput.writeToBuildFile()

        expect:
        succeeds("runInWorker")

        and:
        def operation = buildOperations.only(ExecuteWorkItemBuildOperationType)
        operation.displayName == "LotsOfOutputWorkAction"
        with (operation.details) {
            className == "LotsOfOutputWorkAction"
            displayName == "LotsOfOutputWorkAction"
        }

        and:
        operation.progress.size() == 1000
    }

    def "log messages are still delivered to the build process after a worker action runs"() {
        def lastOutput = ""

        given:
        server.start()

        workActionThatProducesLotsOfOutput.action += """
            new Thread({
                ${server.callFromBuild("beep")}
                println "beep..."
            }).start()
        """
        workActionThatProducesLotsOfOutput.writeToBuildFile()

        buildFile << """
            task block {
                dependsOn runInWorker
                doLast {
                    ${server.callFromTaskAction("block")}
                }
            }
        """

        when:
        def handler = server.expectConcurrentAndBlock("block", "beep")
        def gradle = executer.withTasks("block").start()

        then:
        handler.waitForAllPendingCalls()
        handler.release("beep")

        when:
        ConcurrentTestUtil.poll {
            def newOutput = gradle.standardOutput - lastOutput
            lastOutput = gradle.standardOutput
            assert newOutput.contains("beep...")
        }

        then:
        handler.releaseAll()

        then:
        gradle.waitForFinish()
    }

    def "log messages from a worker daemon are associated with the task that invokes them"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        workActionThatProducesLotsOfOutput.writeToBuildFile()
        buildFile << """
            task runInWorker2(type: WorkerTask) {
                isolationMode = 'processIsolation'
                workActionClass = ${workActionThatProducesLotsOfOutput.name}.class
                dependsOn(runInWorker)
            }
        """

        expect:
        succeeds("runInWorker2")

        and:
        def logOperations = buildOperations.all(ExecuteWorkItemBuildOperationType)
        def taskOperations = logOperations.collect {
            buildOperations.parentsOf(it).reverse().find { parent -> buildOperations.isType(parent, ExecuteTaskBuildOperationType) }
        }.unique()
        taskOperations.size() == 2
        taskOperations.collect {it.displayName }.containsAll(['Task :runInWorker', 'Task :runInWorker2'])

        and:
        logOperations.every { operation -> operation.progress.size() == 1000 }

        and:
        assertSameDaemonWasUsed("runInWorker", "runInWorker2")
    }
}
