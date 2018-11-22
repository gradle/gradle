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
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@IntegrationTestTimeout(120)
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
    def "multiple work items can be executed in parallel in #isolationMode (wait for results: #waitForResults)"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                isolationMode = $isolationMode
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
        blockingHttpServer.expectConcurrent("workItem0", "workItem1", "workItem2")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        where:
        isolationMode           | waitForResults
        'IsolationMode.NONE'    | true
        'IsolationMode.NONE'    | false
        'IsolationMode.PROCESS' | true
        'IsolationMode.PROCESS' | false
        'IsolationMode.CLASSLOADER'  | true
        'IsolationMode.CLASSLOADER'  | false
    }

    @Unroll
    def "multiple work items with different requirements can be executed in parallel in #isolationMode"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                isolationMode = $isolationMode
                doLast {
                    submitWorkItem("workItem0", TestParallelRunnable) { it.classpath([ new File("foo") ]) }
                    submitWorkItem("workItem1", TestParallelRunnable) { it.classpath([ new File("bar") ]) }
                    submitWorkItem("workItem2", TestParallelRunnable) { it.classpath([ new File("baz") ]) }
                }
            }
        """
        blockingHttpServer.expectConcurrent("workItem0", "workItem1", "workItem2")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        where:
        isolationMode << [ "IsolationMode.PROCESS", "IsolationMode.CLASSLOADER" ]
    }

    @Unroll
    def "multiple work items with different actions can be executed in parallel in #isolationMode"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                isolationMode = $isolationMode
                doLast {
                    submitWorkItem("workItem0", AlternateParallelRunnable.class)
                    submitWorkItem("workItem1", TestParallelRunnable.class)
                    submitWorkItem("workItem2", AlternateParallelRunnable.class)
                }
            }
        """
        blockingHttpServer.expectConcurrent("alternate_workItem0", "workItem1", "alternate_workItem2")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "a second task action does not start until all work submitted by a previous task action is complete"() {
        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("taskAction1", runnableClass) { isolationMode = IsolationMode.PROCESS } }
                doLast { submitWorkItem("taskAction2", runnableClass) { isolationMode = IsolationMode.CLASSLOADER } }
                doLast { submitWorkItem("taskAction3", runnableClass) { isolationMode = IsolationMode.PROCESS } }
                doLast { submitWorkItem("taskAction4", runnableClass) { isolationMode = IsolationMode.PROCESS } }
                doLast { submitWorkItem("taskAction5", runnableClass) { isolationMode = IsolationMode.CLASSLOADER } }
                doLast { submitWorkItem("taskAction6", runnableClass) { isolationMode = IsolationMode.CLASSLOADER } }
            }
        """
        blockingHttpServer.expectConcurrent("taskAction1")
        blockingHttpServer.expectConcurrent("taskAction2")
        blockingHttpServer.expectConcurrent("taskAction3")
        blockingHttpServer.expectConcurrent("taskAction4")
        blockingHttpServer.expectConcurrent("taskAction5")
        blockingHttpServer.expectConcurrent("taskAction6")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")
    }

    @Unroll
    def "a second task action does not start if work submitted in #isolationMode by a previous task action fails"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("taskAction1", runnableClass) { isolationMode = $isolationMode } }
                doLast { submitWorkItem("taskAction2", RunnableThatFails.class) }
                doLast { submitWorkItem("taskAction3") }
            }
        """
        blockingHttpServer.expectConcurrent("taskAction1")

        expect:
        args("--max-workers=3")
        fails("parallelWorkTask")

        and:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("Failure from taskAction2")

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "all other submitted work executes when a work item fails in #isolationMode"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("workItem1", runnableClass) { isolationMode = IsolationMode.PROCESS } 
                    submitWorkItem("workItem2", RunnableThatFails.class) { isolationMode = $isolationMode }
                    submitWorkItem("workItem3", runnableClass) { isolationMode = IsolationMode.CLASSLOADER } 
                }
            }
        """
        blockingHttpServer.expectConcurrent("workItem1", "workItem3")

        expect:
        args("--max-workers=3")
        fails("parallelWorkTask")

        and:
        failureHasCause("A failure occurred while executing RunnableThatFails")
        failureHasCause("Failure from workItem2")

        where:
        isolationMode << ISOLATION_MODES
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
        blockingHttpServer.expectConcurrent("taskAction1", "taskAction2")
        blockingHttpServer.expectConcurrent("taskAction3")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")
    }

    @Unroll
    def "all errors are reported when submitting failing work in #isolationModeDescription"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("workItem1", RunnableThatFails.class) { config ->
                        config.isolationMode = $isolationMode1
                        config.displayName = "work item 1"
                    }
                    submitWorkItem("workItem2", RunnableThatFails.class) { config ->
                        config.isolationMode = $isolationMode2
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

        where:
        isolationMode1               | isolationMode2               | isolationModeDescription
        'IsolationMode.NONE'         | 'IsolationMode.NONE'         | 'IsolationMode.NONE'
        'IsolationMode.PROCESS'      | 'IsolationMode.PROCESS'      | 'IsolationMode.PROCESS'
        'IsolationMode.CLASSLOADER'  | 'IsolationMode.CLASSLOADER'  | 'IsolationMode.CLASSLOADER'
        'IsolationMode.PROCESS'      | 'IsolationMode.CLASSLOADER'  | 'both IsolationMode.PROCESS and IsolationMode.CLASSLOADER'
        'IsolationMode.NONE'         | 'IsolationMode.CLASSLOADER'  | 'both IsolationMode.NONE and IsolationMode.CLASSLOADER'
    }

    @Unroll
    def "both errors in work items in #isolationMode and errors in the task action are reported"() {
        given:
        buildFile << """
            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                isolationMode = $isolationMode
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

        where:
        isolationMode << ISOLATION_MODES
    }

    @Unroll
    def "user can take responsibility for failing work items in #isolationMode"() {
        given:
        buildFile << """
            import java.util.concurrent.ExecutionException
            import org.gradle.workers.WorkerExecutionException

            $runnableThatFails

            task parallelWorkTask(type: MultipleWorkItemTask) {
                isolationMode = $isolationMode
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
        blockingHttpServer.expectConcurrent("workItem1")

        expect:
        args("--max-workers=3")
        succeeds("parallelWorkTask")

        and:
        output.contains("A failure occurred while executing work item 2")

        where:
        isolationMode << ISOLATION_MODES
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

        // warm buildSrc
        succeeds("help")

        def calls = ["workItem0", "workItem1", "workItem2", "workItem3", "workItem4", "workItem5"] as String[]
        def handler = blockingHttpServer.expectConcurrentAndBlock(maxWorkers, calls)

        when:
        args("--max-workers=${maxWorkers}")
        executer.withTasks("parallelWorkTask")
        def gradle = executer.start()

        then:
        handler.waitForAllPendingCalls()

        when:
        handler.release(1)

        then:
        handler.waitForAllPendingCalls()

        when:
        handler.release(2)

        then:
        handler.waitForAllPendingCalls()

        when:
        handler.release(3)

        then:
        gradle.waitForFinish()
    }

    def "does not start more than max-workers threads when work items do not submit more work"() {
        def maxWorkers = 3
        def workItems = 200

        given:
        buildFile << """
            task parallelWorkTask(type: MultipleWorkItemTask) {
                doLast {
                    ${workItems}.times { i ->
                        submitWorkItem("workItem\${i}")
                    }
                }
                doLast {
                    def threadGroup = Thread.currentThread().threadGroup
                    println "\\nWorker Executor threads:"
                    def threads = new Thread[threadGroup.activeCount()]
                    threadGroup.enumerate(threads) 
                    def executorThreads = threads.findAll { it?.name.startsWith("${WorkerExecutionQueueFactory.QUEUE_DISPLAY_NAME}") } 
                    executorThreads.each { println it }
                    
                    // Ensure that we don't leave any threads lying around
                    assert executorThreads.size() <= ${maxWorkers}
                }
            }
        """

        // warm buildSrc
        succeeds("help")

        def calls = []
        workItems.times { i -> calls << "workItem${i}" }
        def handler = blockingHttpServer.expectConcurrentAndBlock(maxWorkers, calls as String[])

        when:
        args("--max-workers=${maxWorkers}")
        executer.withTasks("parallelWorkTask")
        def gradle = executer.start()

        then:
        workItems.times {
            handler.waitForAllPendingCalls()
            handler.release(1)
        }

        then:
        gradle.waitForFinish()
    }

    def "does not start more daemons than max-workers"() {
        def maxWorkers = 3

        given:
        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            
            task parallelWorkTask(type: MultipleWorkItemTask) {
                isolationMode = IsolationMode.PROCESS
                doLast {
                    6.times { i ->
                        submitWorkItem("workItem\${i}")
                    }
                }
                doLast {
                    assert services.get(WorkerDaemonClientsManager).allClients.size() == 3
                }
            }
        """

        // warm buildSrc
        succeeds("help")

        def calls = ["workItem0", "workItem1", "workItem2", "workItem3", "workItem4", "workItem5"] as String[]
        def handler = blockingHttpServer.expectConcurrentAndBlock(maxWorkers, calls)

        when:
        args("--max-workers=${maxWorkers}")
        def gradle = executer.withTasks("parallelWorkTask")
                        .requireIsolatedDaemons()
                        .withWorkerDaemonsExpirationDisabled()
                        .start()

        then:
        handler.waitForAllPendingCalls()

        when:
        handler.release(3)

        then:
        handler.waitForAllPendingCalls()

        when:
        handler.release(3)

        then:
        handler.waitForAllPendingCalls()

        then:
        gradle.waitForFinish()
    }

    def "work items in the same task reuse daemons"() {
        def maxWorkers = 1

        given:
        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            
            task parallelWorkTask(type: MultipleWorkItemTask) {
                isolationMode = IsolationMode.PROCESS
                doLast {
                    2.times { i ->
                        submitWorkItem("workItem\${i}")
                    }
                }
            }
        """

        // warm buildSrc
        succeeds("help")

        def calls = ["workItem0", "workItem1"] as String[]
        def handler = blockingHttpServer.expectConcurrentAndBlock(maxWorkers, calls)

        when:
        args("--max-workers=${maxWorkers}")
        executer.withTasks("parallelWorkTask")
        def gradle = executer.withWorkerDaemonsExpirationDisabled().start()

        then:
        handler.waitForAllPendingCalls()

        when:
        handler.release(1)

        then:
        handler.waitForAllPendingCalls()

        when:
        handler.release(1)

        then:
        handler.waitForAllPendingCalls()

        then:
        gradle.waitForFinish()

        and:
        assertSameDaemonWasUsed("workItem0", "workItem1")
    }

    def "can start another task when the current task is waiting on async work"() {
        given:
        buildFile << """
            task firstTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("task1") }
            }
            
            task secondTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("task2") }
            }
            
            task allTasks {
                dependsOn firstTask, secondTask
            }
        """

        blockingHttpServer.expectConcurrent("task1", "task2")

        expect:
        args("--max-workers=2")
        succeeds("allTasks")
    }

    def "can start another task when the current task has multiple actions and is waiting on async work"() {
        given:
        buildFile << """
            task firstTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("task1-1") }
                doLast { submitWorkItem("task1-2") }
            }
            
            task secondTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("task2") }
            }
            
            task allTasks {
                dependsOn firstTask, secondTask
            }
        """

        blockingHttpServer.expectConcurrent("task1-1", "task2")
        blockingHttpServer.expectConcurrent("task1-2")

        expect:
        args("--max-workers=2")
        succeeds("allTasks")
    }

    def "does not start a task in another project when a task action is executing without --parallel"() {
        given:
        settingsFile << """
            include ':childProject'
        """
        buildFile << """
            task firstTask(type: MultipleWorkItemTask) {
                doLast { ${blockingHttpServer.callFromBuild("task1")} }
            }
            
            project(':childProject') {
                task secondTask(type: MultipleWorkItemTask) {
                    doLast { submitWorkItem("task2") }
                }
            }
            
            task allTasks {
                dependsOn firstTask, project(':childProject').secondTask
            }
        """

        blockingHttpServer.expectConcurrent("task1")
        blockingHttpServer.expectConcurrent("task2")

        expect:
        args("--max-workers=2")
        succeeds("allTasks")
    }

    def "can start a task in another project when a task is waiting for async work without --parallel"() {
        given:
        settingsFile << """
            include ':childProject'
        """
        buildFile << """
            task firstTask(type: MultipleWorkItemTask) {
                doLast { submitWorkItem("task1") }
            }
            
            project(':childProject') {
                task secondTask(type: MultipleWorkItemTask) {
                    doLast { ${blockingHttpServer.callFromBuild("task2")} }
                }
            }
            
            task allTasks {
                dependsOn firstTask, project(':childProject').secondTask
            }
        """

        blockingHttpServer.expectConcurrent("task1", "task2")

        expect:
        args("--max-workers=2")
        succeeds("allTasks")
    }

    def "does not start another task when the user is waiting on async work"() {
        given:
        buildFile << """
            task firstTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("task1-1") 
                    workerExecutor.await()
                    ${blockingHttpServer.callFromBuild("task1-2")}
                }
            }
            
            task secondTask(type: MultipleWorkItemTask) {
                doLast { ${blockingHttpServer.callFromBuild("task2")} }
            }
            
            task allTasks {
                dependsOn firstTask, secondTask
            }
        """

        blockingHttpServer.expectConcurrent("task1-1")
        blockingHttpServer.expectConcurrent("task1-2")
        blockingHttpServer.expectConcurrent("task2")

        expect:
        args("--max-workers=3")
        succeeds("allTasks")
    }

    def "does not start another task in a different project when the user is waiting on async work"() {
        given:
        settingsFile << """
            include ':childProject'
        """
        buildFile << """
            task firstTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("task1-1") 
                    workerExecutor.await()
                    ${blockingHttpServer.callFromBuild("task1-2")}
                }
            }
            
            project(':childProject') {
                task secondTask(type: MultipleWorkItemTask) {
                    doLast { ${blockingHttpServer.callFromBuild("task2")} }
                }
            }
            
            task allTasks {
                dependsOn firstTask, project(':childProject').secondTask
            }
        """

        blockingHttpServer.expectConcurrent("task1-1")
        blockingHttpServer.expectConcurrent("task1-2")
        blockingHttpServer.expectConcurrent("task2")

        expect:
        args("--max-workers=3")
        succeeds("allTasks")
    }

    def "can start another task in a different project when the user is waiting on async work with --parallel"() {
        given:
        settingsFile << """
            include ':childProject'
        """
        buildFile << """
            task firstTask(type: MultipleWorkItemTask) {
                doLast { 
                    submitWorkItem("task1-1") 
                    workerExecutor.await()
                    ${blockingHttpServer.callFromBuild("task1-2")}
                }
            }
            
            project(':childProject') {
                task secondTask(type: MultipleWorkItemTask) {
                    doLast { ${blockingHttpServer.callFromBuild("task2")} }
                }
            }
            
            task allTasks {
                dependsOn firstTask, project(':childProject').secondTask
            }
        """

        blockingHttpServer.expectConcurrent("task1-1", "task2")
        blockingHttpServer.expectConcurrent("task1-2")

        expect:
        args("--max-workers=3", "--parallel")
        succeeds("allTasks")
    }

    def "cannot mutate worker parameters when using IsolationMode.NONE"() {
        given:
        buildFile << """
            List<String> mutableList = ["foo"]
            
            class VerifyingRunnable implements Runnable {
                final String itemName
                final List<String> list

                @Inject
                public VerifyingRunnable(String itemName, List<String> list) {
                    this.itemName = itemName
                    this.list = list
                }

                public void run() {
                    assert list.size() == 1
                    new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${itemName}", null, null).toURL().text
                    assert list.size() == 1
                }
            }
            
            class VerifyingRunnableTask extends DefaultTask {
                String itemName
                List<String> list
                
                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }
                
                @TaskAction
                void executeRunnable() {
                    workerExecutor.submit(VerifyingRunnable.class) { config ->
                        config.isolationMode = IsolationMode.NONE
                        config.params = [ itemName.toString(), list ]
                    }
                }
            }
            
            task firstTask(type: VerifyingRunnableTask) {
                itemName = "task1"
                list = mutableList
            }
            
            task secondTask(type: MultipleWorkItemTask) {
                doLast { 
                    mutableList.add "bar"
                    assert mutableList.size() == 2
                    submitWorkItem("task2") 
                }
            }
        """

        blockingHttpServer.expectConcurrent("task1", "task2")

        expect:
        args("--max-workers=2")
        succeeds("firstTask", "secondTask")
    }

    def getParallelRunnable() {
        return """
            import java.net.URI
            import javax.inject.Inject
            import org.gradle.test.FileHelper

            public class TestParallelRunnable implements Runnable {
                final String itemName
                private static final String id = UUID.randomUUID().toString()

                @Inject
                public TestParallelRunnable(String itemName) {
                    this.itemName = itemName
                }
                
                public void run() {
                    System.out.println("Running \${itemName}...")
                    new URI("http", null, "localhost", ${blockingHttpServer.getPort()}, "/\${itemName}", null, null).toURL().text
                    File outputDir = new File("${fixture.outputFileDirPath}")
                    File outputFile = new File(outputDir, itemName)
                    FileHelper.write(id, outputFile)
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
            import javax.inject.Inject

            public class AlternateParallelRunnable implements Runnable {
                final String itemName 

                @Inject
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

            class MultipleWorkItemTask extends DefaultTask {
                def isolationMode = IsolationMode.NONE
                def additionalForkOptions = {}
                def runnableClass = TestParallelRunnable.class
                def additionalClasspath = project.layout.files()

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
                        config.isolationMode = this.isolationMode
                        if (config.isolationMode == IsolationMode.PROCESS) {
                            config.forkOptions.maxHeapSize = "64m"
                        }
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
            import javax.inject.Inject
            
            public class RunnableThatFails implements Runnable {
                private final String itemName
                
                @Inject
                public RunnableThatFails(String itemName) { 
                    this.itemName = itemName
                }

                public void run() {
                    throw new RuntimeException("Failure from \${itemName}");
                }
            }
        """
    }

    @Override
    void assertSameDaemonWasUsed(String task1, String task2) {
        assert outputFileDir.file(task1).text == outputFileDir.file(task2).text
    }
}
