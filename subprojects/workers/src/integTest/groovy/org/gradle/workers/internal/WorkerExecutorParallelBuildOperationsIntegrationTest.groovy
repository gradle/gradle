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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf

import java.time.Instant

@IntegrationTestTimeout(120)
@IgnoreIf({ GradleContextualExecuter.parallel })
class WorkerExecutorParallelBuildOperationsIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Rule
    BlockingHttpServer blockingHttpServer = new BlockingHttpServer()
    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        blockingHttpServer.start()
        withMultipleActionTaskTypeInBuildScript()
        buildFile << """
            task slowTask {
                doLast { 
                    ${blockingHttpServer.callFromBuild("slowTask")} 
                    sleep 10
                }
            }
"""
    }

    def "worker-based task completes as soon as work items are finished (while another task is executing in parallel)"() {
        when:
        buildFile << """
            task workTask(type: MultipleWorkItemTask) {
                doLast {
                    println "pre-action"
                }
                doLast { 
                    submitWorkItem("workTask")
                }
            }
            
        """

        then:
        workTaskCompletesFirst()
    }

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

    @NotYetImplemented
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

    private void workTaskCompletesFirst() {
        invokeBuild()
        assert endTime(":workTask").isBefore(endTime(":slowTask"))
    }

    private void workTaskDoesNotCompleteFirst() {
        invokeBuild()
        assert !endTime(":workTask").isBefore(endTime(":slowTask"))
    }

    private void invokeBuild() {
        blockingHttpServer.expectConcurrent("workTask", "slowTask")
        args("--max-workers=4")
        succeeds(":workTask", ":slowTask")
    }

    def endTime(String taskPath) {
        def timeMs = buildOperations.only("Task " + taskPath).endTime
        return Instant.ofEpochMilli(timeMs)
    }

    String getMultipleActionTaskType() {
        return """
            import java.net.URI
            import javax.inject.Inject
            import org.gradle.test.FileHelper
            import org.gradle.workers.WorkerExecutor

            public class TestParallelRunnable implements Runnable {
                final String itemName

                @Inject
                public TestParallelRunnable(String itemName) {
                    this.itemName = itemName
                }
                
                public void run() {
                    System.out.println("Running \${itemName}...")
                    new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${itemName}", null, null).toURL().text
                }
            }

            class MultipleWorkItemTask extends DefaultTask {
                def isolationMode = IsolationMode.NONE
                def runnableClass = TestParallelRunnable.class

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }
                
                def submitWorkItem(item) {
                    return workerExecutor.submit(TestParallelRunnable.class) { config ->
                        config.isolationMode = IsolationMode.NONE
                        config.params = [ item.toString() ]
                    }
                }
            }
        """
    }

    def withMultipleActionTaskTypeInBuildScript() {
        buildFile << """
            $multipleActionTaskType
        """
    }
}
