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

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.workers.fixtures.WorkerExecutorFixture
import org.junit.Rule

import java.time.Instant

@IntegrationTestTimeout(120)
@Requires(IntegTestPreconditions.NotParallelExecutor)
class WorkerExecutorParallelBuildOperationsIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Rule
    BlockingHttpServer blockingHttpServer = new BlockingHttpServer()
    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)
    WorkerExecutorFixture.WorkParameterClass parallelParameterType
    WorkerExecutorFixture.WorkActionClass parallelWorkAction


    def setup() {
        blockingHttpServer.start()

        buildFile << """
            task slowTask {
                doLast {
                    ${blockingHttpServer.callFromBuild("slowTask")}
                }
            }
        """

        parallelParameterType = fixture.workParameterClass("ParallelParameter", "org.gradle.test").withFields([
                "itemName": "String"
        ])

        parallelWorkAction = fixture.workActionClass("ParallelWorkAction", "org.gradle.test", parallelParameterType)
        parallelWorkAction.with {
            imports += ["java.net.URI"]
            action = """
                System.out.println("Running \${parameters.itemName}...")
                new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${parameters.itemName}", null, null).toURL().text
            """
        }

        withMultipleActionTaskTypeInBuildScript()
    }

    def "worker-based task completes as soon as work items are finished (while another task is executing in parallel)"() {
        when:
        createDirs("childProject")
        settingsFile << """
            include ':childProject'
        """
        buildFile << """
            task workTask(type: MultipleWorkItemTask) {
                doLast {
                    submitWorkItem("workTask")
                }
            }

            // Wait for dependent task, to ensure that work task finishes first
            slowTask.doLast {
                ${blockingHttpServer.callFromBuild("slowTask2")}
            }

            project(':childProject') {
                task dependsOnWorkTask(type: MultipleWorkItemTask) {
                    doLast {
                        submitWorkItem("dependsOnWorkTask")
                    }

                    dependsOn project(':').workTask
                }
            }
        """

        then:
        blockingHttpServer.expectConcurrent("workTask", "slowTask")
        blockingHttpServer.expectConcurrent("dependsOnWorkTask", "slowTask2")

        args("--max-workers=4", "--parallel")
        succeeds("dependsOnWorkTask", "slowTask")

        assert endTime(":workTask").isBefore(endTime(":slowTask"))
    }

    @Requires(
        value = IntegTestPreconditions.NotConfigCached,
        reason = """Assumptions about project locking do not hold.
With CC enabled, the project is immutable so tasks run in parallel.
This means workTask and slowTask would be expected to run concurrently in this case."""
    )
    def "worker-based task with further actions does not complete when work items finish (while another task is executing in parallel)"() {
        when:
        buildFile << """
            task workTask(type: MultipleWorkItemTask) {
                doLast {
                    submitWorkItem("workTask")
                }
                doLast {
                    println "post-action"
                }
            }
        """

        then:
        workTaskDoesNotCompleteFirst()
    }

    @UnsupportedWithConfigurationCache
    def "worker-based task with task action listener does not complete while another task is executing in parallel"() {
        when:
        buildFile << """
            gradle.addListener(new TaskActionListener() {
                void beforeActions(Task task) {}
                void afterActions(Task task) {}
            })

            task workTask(type: MultipleWorkItemTask) {
                doLast {
                    submitWorkItem("workTask")
                }
            }
        """

        then:
        workTaskDoesNotCompleteFirst()
    }

    @UnsupportedWithConfigurationCache
    def "worker-based task with task execution listener does not complete while another task is executing in parallel"() {
        when:
        buildFile << """
            gradle.addListener(new TaskExecutionListener() {
                void beforeExecute(Task task) {}
                void afterExecute(Task task, TaskState state) {}
            })

            task workTask(type: MultipleWorkItemTask) {
                doLast {
                    submitWorkItem("workTask")
                }
            }
        """

        then:
        workTaskDoesNotCompleteFirst()
    }

    private void workTaskDoesNotCompleteFirst() {
        blockingHttpServer.expectConcurrent("workTask", "slowTask")
        args("--max-workers=4")
        succeeds(":workTask", ":slowTask")
        assert !endTime(":workTask").isBefore(endTime(":slowTask"))
    }

    def endTime(String taskPath) {
        def timeMs = buildOperations.only("Task " + taskPath).endTime
        return Instant.ofEpochMilli(timeMs)
    }

    String getMultipleActionTaskType() {
        return """
            import org.gradle.test.FileHelper

            class MultipleWorkItemTask extends DefaultTask {
                @Internal
                def isolationMode = 'noIsolation'

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }

                def submitWorkItem(item) {
                    return workerExecutor.noIsolation().submit(${parallelWorkAction.name}.class) {
                        itemName = item.toString()
                    }
                }
            }
        """
    }

    def withMultipleActionTaskTypeInBuildScript() {
        parallelWorkAction.writeToBuildFile()
        buildFile << """
            $multipleActionTaskType
        """
    }
}
