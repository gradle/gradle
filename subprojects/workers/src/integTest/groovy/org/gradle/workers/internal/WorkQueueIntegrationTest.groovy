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

import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.workers.fixtures.WorkerExecutorFixture
import org.junit.Rule

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

class WorkQueueIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Rule BlockingHttpServer blockingHttpServer = new BlockingHttpServer()
    WorkerExecutorFixture.WorkParameterClass parallelParameterType
    WorkerExecutorFixture.WorkActionClass parallelWorkAction

    def setup() {
        blockingHttpServer.start()

        parallelParameterType = fixture.workParameterClass("ParallelParameter", "org.gradle.test").withFields([
                "itemName": "String",
                "shouldFail": "Boolean"
        ])

        parallelWorkAction = fixture.workActionClass("ParallelWorkAction", "org.gradle.test", parallelParameterType)
        parallelWorkAction.with {
            imports += ["java.net.URI", "org.gradle.test.FileHelper"]
            extraFields = "private static final String id = UUID.randomUUID().toString()"
            action = """
                System.out.println("Running \${parameters.itemName}...")
                new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${parameters.itemName}", null, null).toURL().text
                if (parameters.shouldFail) {
                    throw new Exception("Failure from " + parameters.itemName)
                }
                File outputDir = new File("${fixture.outputFileDirPath}")
                File outputFile = new File(outputDir, parameters.itemName)
                FileHelper.write(id, outputFile)
            """
        }
        parallelWorkAction.writeToBuildFile()

        buildFile << """
            ${workItemTask}
        """
    }

    def "can wait on work items submitted to a queue with #isolationMode"() {
        buildFile << """
            task runWork(type: WorkItemTask) {
                doLast {
                    def workQueue1 = submit(ParallelWorkAction.class, [ "item1", "item2"])
                    def workQueue2 = submit(ParallelWorkAction.class, [ "item3" ])

                    signal("submitted")

                    workQueue1.await()

                    signal("finished")
                }
            }
        """

        def started = blockingHttpServer.expectConcurrentAndBlock("item1", "item2", "item3", "submitted")
        def finished = blockingHttpServer.expectConcurrentAndBlock("finished")

        when:
        def gradle = executer.withTasks("runWork").start()

        then:
        started.waitForAllPendingCalls()

        then:
        started.release("submitted")
        started.release("item1")
        started.release("item2")

        then:
        finished.waitForAllPendingCalls()

        then:
        finished.releaseAll()
        started.release("item3")

        then:
        gradle.waitForFinish()

        and:
        assertWorkItemsExecuted("item1", "item2", "item3")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "all errors are reported when waiting on work submitted to a queue in #isolationMode"() {
        buildFile << """
            task runWork(type: WorkItemTask) {
                doLast {
                    def workQueue1 = submitFailure(ParallelWorkAction.class, [ "item1", "item2"])
                    def workQueue2 = submit(ParallelWorkAction.class, [ "item3" ])

                    signal("submitted")

                    try {
                        workQueue1.await()
                    } catch (Exception e) {
                        printMessages(e)
                    }

                    signal("finished")
                }
            }
        """

        def started = blockingHttpServer.expectConcurrentAndBlock("item1", "item2", "item3", "submitted")
        def finished = blockingHttpServer.expectConcurrentAndBlock("finished")

        when:
        def gradle = executer.withTasks("runWork").start()

        then:
        started.waitForAllPendingCalls()

        then:
        started.release("submitted")
        started.release("item1")
        started.release("item2")

        then:
        finished.waitForAllPendingCalls()

        then:
        finished.releaseAll()
        started.release("item3")

        then:
        def result = gradle.waitForFinish()

        and:
        assertWorkItemsExecuted("item3")

        and:
        result.groupedOutput.task(":runWork").output.readLines().containsAll("Failure from item1", "Failure from item2")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "errors in work submitted to other queues cause a task failure when waiting for work in #isolationMode"() {
        buildFile << """
            task runWork(type: WorkItemTask) {
                doLast {
                    def workQueue1 = submitFailure(ParallelWorkAction.class, [ "item1", "item2"])
                    def workQueue2 = submitFailure(ParallelWorkAction.class, [ "item3" ])

                    signal("submitted")

                    try {
                        workQueue1.await()
                    } catch (Exception e) {
                        printMessages(e)
                    }

                    signal("finished")
                }
            }
        """

        def started = blockingHttpServer.expectConcurrentAndBlock("item1", "item2", "item3", "submitted")
        def finished = blockingHttpServer.expectConcurrentAndBlock("finished")

        when:
        def gradle = executer.withTasks("runWork").start()

        then:
        started.waitForAllPendingCalls()

        then:
        started.release("submitted")
        started.release("item1")
        started.release("item2")

        then:
        finished.waitForAllPendingCalls()

        then:
        finished.releaseAll()
        started.release("item3")

        then:
        def result = gradle.waitForFailure()

        and:
        result.groupedOutput.task(":runWork").output.readLines().containsAll("Failure from item1", "Failure from item2")

        and:
        result.assertHasNoCause("Failure from item1")
        result.assertHasNoCause("Failure from item2")
        result.assertHasCause("Failure from item3")

        where:
        isolationMode << ISOLATION_MODES
    }

    void assertWorkItemsExecuted(String... items) {
        File outputDir = new File("${fixture.outputFileDirPath}")
        assert items.every { item ->
            new File(outputDir, item).exists()
        }
    }

    String getWorkItemTask() {
        return """
            import org.gradle.internal.exceptions.MultiCauseException

            class WorkItemTask extends DefaultTask {
                @Internal
                String isolationMode = 'noIsolation'

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }

                def submitFailure(Class<?> executionClass, List<String> items) {
                    return submit(executionClass, items, true)
                }

                def submit(Class<?> executionClass, List<String> items, boolean error = false) {
                    def workQueue = workerExecutor."\${isolationMode}"()
                    items.each { name ->
                        workQueue.submit(executionClass) {
                            itemName = name
                            shouldFail = error
                        }
                    }
                    return workQueue
                }

                def signal(String signal) {
                    new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${signal}", null, null).toURL().text
                }

                def printMessages(Exception e) {
                    println e.message
                    if (e.cause == null || e.cause == e) {
                        return
                    }
                    if (e instanceof MultiCauseException) {
                        e.causes.each {
                            printMessages(it)
                        }
                    } else {
                        printMessages(e.cause)
                    }
                }
            }
        """
    }
}
