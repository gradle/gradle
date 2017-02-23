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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll

@Timeout(60)
@IgnoreIf({ GradleContextualExecuter.parallel })
class WorkerExecutorParallelIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    @Rule
    BlockingHttpServer blockingHttpServer = new BlockingHttpServer()

    def setup() {
        blockingHttpServer.start()
        withParallelRunnableInBuildScript()
        withAlternateRunnableInBuildScript()
        withMultipleActionTaskTypeInBuildScript()
    }

    @Unroll
    def "multiple #execModel work items can be executed in parallel (wait for results: #waitForResults)"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                fork = $doFork
                doLast {
                    submitWorkItem("workItem0")
                    submitWorkItem("workItem1")
                    submitWorkItem("workItem2")
                    
                    if (${waitForResults}) {
                        workerExecutor.await()
                    }
                }
            }
        """
        blockingHttpServer.expectConcurrentExecution("workItem0", "workItem1", "workItem2")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        where:
        doFork | execModel    | waitForResults
        true   | 'daemon'     | true
        true   | 'daemon'     | false
        false  | 'in-process' | true
        false  | 'in-process' | false
    }

    @Unroll
    def "multiple #execModel work items with different requirements can be executed in parallel"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                fork = $doFork
                additionalForkOptions = { options ->
                    options.systemProperty("now", System.currentTimeMillis())
                }
                doLast {
                    submitWorkItem("workItem0")
                    submitWorkItem("workItem1")
                    submitWorkItem("workItem2")
                }
            }
        """
        blockingHttpServer.expectConcurrentExecution("workItem0", "workItem1", "workItem2")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    @Unroll
    def "multiple #execModel work items with different actions can be executed in parallel"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                fork = $doFork
                doLast {
                    submitWorkItem("workItem0", AlternateParallelRunnable.class)
                    submitWorkItem("workItem1", TestParallelRunnable.class)
                    submitWorkItem("workItem2", AlternateParallelRunnable.class)
                }
            }
        """
        blockingHttpServer.expectConcurrentExecution("alternate_workItem0", "workItem1", "alternate_workItem2")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    def "a second task action does not start until all work submitted by a previous task action is complete"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("taskAction1", runnableClass) { fork = true } }
                doLast { submitWorkItem("taskAction2", runnableClass) { fork = false } }
                doLast { submitWorkItem("taskAction3", runnableClass) { fork = true } }
                doLast { submitWorkItem("taskAction4", runnableClass) { fork = true } }
                doLast { submitWorkItem("taskAction5", runnableClass) { fork = false } }
                doLast { submitWorkItem("taskAction6", runnableClass) { fork = false } }
            }
        """
        blockingHttpServer.expectConcurrentExecution("taskAction1")
        blockingHttpServer.expectConcurrentExecution("taskAction2")
        blockingHttpServer.expectConcurrentExecution("taskAction3")
        blockingHttpServer.expectConcurrentExecution("taskAction4")
        blockingHttpServer.expectConcurrentExecution("taskAction5")
        blockingHttpServer.expectConcurrentExecution("taskAction6")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")
    }

    @Unroll
    def "a second task action does not start if #execModel work submitted by a previous task action fails"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("taskAction1", runnableClass) { fork = $doFork } }
                doLast { submitWorkItem("taskAction2", RunnableThatFails.class) }
                doLast { submitWorkItem("taskAction3") }
            }
        """
        blockingHttpServer.expectConcurrentExecution("taskAction1")

        expect:
        args("--max-workers=3")
        fails("parallelWorkTask")

        and:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("Failure from taskAction2")

        and:
        errorOutput.contains("Caused by: java.lang.RuntimeException: Failure from taskAction2")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    @Unroll
    def "all other submitted work executes when a #execModel work item fails"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("workItem1", runnableClass) { fork = true } 
                    submitWorkItem("workItem2", RunnableThatFails.class) { fork = $doFork }
                    submitWorkItem("workItem3", runnableClass) { fork = false } 
                }
            }
        """
        blockingHttpServer.expectConcurrentExecution("workItem1", "workItem3")

        expect:
        args("--max-workers=3")
        fails("parallelWorkTask")

        and:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("Failure from workItem2")

        and:
        errorOutput.contains("Caused by: java.lang.RuntimeException: Failure from workItem2")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    def "a task that depends on a task with work does not start until the work is complete"() {
        given:
        buildFile << """
            task anotherParallelWorkTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("taskAction1")  
                    submitWorkItem("taskAction2") 
                }
            }
            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("taskAction3") }
                
                dependsOn anotherParallelWorkTask
            }
        """
        blockingHttpServer.expectConcurrentExecution("taskAction1", "taskAction2")
        blockingHttpServer.expectConcurrentExecution("taskAction3")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")
    }

    @Unroll
    def "all errors are reported when submitting #execModel failing work"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("workItem1", RunnableThatFails.class) { config ->
                        config.fork = $doFork1
                        config.displayName = "work item 1"
                    }
                    submitWorkItem("workItem2", RunnableThatFails.class) { config ->
                        config.fork = $doFork2
                        config.displayName = "work item 2"
                    }
                }
            }
        """

        expect:
        args("--max-workers=3")
        fails("parallelWorkTask")

        and:
        failureHasCause("Multiple task action failures occurred")

        and:
        failureHasCause("A failure occurred while executing work item 1")
        failureHasCause("Failure from workItem1")

        and:
        failureHasCause("A failure occurred while executing work item 2")
        failureHasCause("Failure from workItem2")

        and: // TODO REMOVE THIS?
        errorOutput.contains("Failure from workItem1")
        errorOutput.contains("Failure from workItem2")

        where:
        doFork1 | doFork2 | execModel
        true    | true    | 'daemon'
        false   | false   | 'in-process'
        true    | false   | 'both daemon and in-process'
    }

    @Unroll
    def "both errors in #execModel work items and errors in the task action are reported"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                fork = $doFork
                doLast { 
                    submitWorkItem("workItem1", RunnableThatFails.class) { config ->
                        config.displayName = "work item 1"
                    }
                    throw new RuntimeException("Failure from task action")
                }
            }
        """

        expect:
        args("--max-workers=3")
        fails("parallelWorkTask")

        and:
        failureHasCause("Multiple task action failures occurred")

        and:
        failureHasCause("Failure from task action")

        and:
        failureHasCause("A failure occurred while executing work item 1")
        failureHasCause("Failure from workItem1")

        and: //TODO REMOVE THIS?
        errorOutput.contains("Failure from workItem1")
        errorOutput.contains("Failure from task action")

        where:
        doFork | execModel
        true   | 'daemon'
        false  | 'in-process'
    }

    @Unroll
    def "user can take responsibility for failing #execModel work items (using: #waitMethod)"() {
        given:
        buildFile << """
            import java.util.concurrent.ExecutionException
            import org.gradle.workers.WorkerExecutionException

            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                fork = $doFork
                doLast { 
                    submitWorkItem("workItem1", runnableClass)

                    submitWorkItem("workItem2", RunnableThatFails.class) { config ->
                        config.displayName = "work item 2"
                    }

                    try {
                        workerExecutor.await()
                    } catch (ExecutionException e) {
                        logger.warn e.message
                    } catch (WorkerExecutionException e) {
                        logger.warn e.causes[0].message
                    }
                }
            }
        """
        blockingHttpServer.expectConcurrentExecution("workItem1")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        and:
        output.contains("A failure occurred while executing work item 2")

        where:
        waitMethod                       | doFork | execModel
        'result.get()'                   | true   | 'daemon'
        'result.get()'                   | false  | 'in-process'
        'workerExecutor.await([result])' | true   | 'daemon'
        'workerExecutor.await([result])' | false  | 'in-process'
    }

    def "max workers is honored by parallel work"() {
        def maxWorkers = 3

        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast {
                    6.times { i ->
                        submitWorkItem("workItem\${i}")
                    }
                }
            }
        """
        def calls = ["workItem0", "workItem1", "workItem2", "workItem3", "workItem4", "workItem5"] as String[]
        def handler = blockingHttpServer.blockOnConcurrentExecutionAnyOf(maxWorkers, calls)

        when:
        args("--max-workers=${maxWorkers}")
        executer.withTasks("parallelWorkTask")
        def gradle = executer.start()

        then:
        handler.waitForAllPendingCalls(30)

        when:
        handler.release(1)

        then:
        handler.waitForAllPendingCalls(10)

        when:
        handler.release(2)

        then:
        handler.waitForAllPendingCalls(10)

        when:
        handler.release(3)

        then:
        gradle.waitForFinish()
    }

    def getParallelRunnable() {
        return """
            import java.net.URI

            public class TestParallelRunnable implements Runnable {
                final String itemName 

                public TestParallelRunnable(String itemName) {
                    this.itemName = itemName
                }
                
                public void run() {
                    System.out.println("Running \${itemName}...")
                    new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${itemName}", null, null).toURL().text
                }
            }
        """
    }

    def withParallelRunnableInBuildScript() {
        buildFile << """
            $parallelRunnable
        """
    }

    def getAlternateParallelRunnable() {
        return """
            import java.net.URI

            public class AlternateParallelRunnable implements Runnable {
                final String itemName 

                public AlternateParallelRunnable(String itemName) {
                    this.itemName = itemName
                }
                
                public void run() {
                    System.out.println("Running alternate_\${itemName}...")
                    new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/alternate_\${itemName}", null, null).toURL().text
                }
            }
        """
    }

    def withAlternateRunnableInBuildScript() {
        buildFile << """
            $alternateParallelRunnable
        """
    }

    String getMultipleActionTaskType() {
        return """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor

            @ParallelizableTask
            class MultipleWorkItemTask extends DefaultTask {
                def fork = false
                def additionalForkOptions = {}
                def runnableClass = TestParallelRunnable.class
                def additionalClasspath = project.files()

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }
                
                def submitWorkItem(item) {
                    return submitWorkItem(item, runnableClass) 
                }
                
                def submitWorkItem(item, actionClass) {
                    return submitWorkItem(item, actionClass, {})
                }
                
                def submitWorkItem(item, actionClass, configClosure) {
                    return workerExecutor.submit(actionClass) { config ->
                        config.fork = fork
                        config.forkOptions(additionalForkOptions)
                        config.classpath(additionalClasspath)
                        config.params = [ item.toString() ]
                        configClosure.call(config)
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

    String getRunnableThatFails() {
        return """
            public class RunnableThatFails implements Runnable {
                private final String itemName
                
                public RunnableThatFails(String itemName) { 
                    this.itemName = itemName
                }

                public void run() {
                    throw new RuntimeException("Failure from \${itemName}");
                }
            }
        """
    }
}
