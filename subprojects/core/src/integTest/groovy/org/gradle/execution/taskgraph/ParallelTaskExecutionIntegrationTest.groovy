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

package org.gradle.execution.taskgraph

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.parallel })
// no point, always runs in parallel
class ParallelTaskExecutionIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def setup() {
        blockingServer.start()

        settingsFile << 'include "a", "b"'

        buildFile << """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor

            class SerialPing extends DefaultTask {
                @TaskAction
                void ping() {
                    URL url = new URL("http://localhost:${blockingServer.port}/" + path)
                    url.openConnection().getHeaderField('RESPONSE')
                }
            }

            public class TestParallelRunnable implements Runnable {
                final String path 

                @Inject
                public TestParallelRunnable(String path) {
                    this.path = path
                }
                
                public void run() {
                    URL url = new URL("http://localhost:${blockingServer.port}/" + path)
                    url.openConnection().getHeaderField('RESPONSE')
                }
            }
            
            class Ping extends DefaultTask {
                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }
                
                @TaskAction
                void ping() {
                    workerExecutor.submit(TestParallelRunnable) { config ->
                        config.params = [ path ]
                    }
                }
            }

            class FailingPing extends DefaultTask {
                @TaskAction
                void ping() {
                    URL url = new URL("http://localhost:${blockingServer.port}/" + path)
                    url.openConnection().getHeaderField('RESPONSE')
                    throw new RuntimeException("task failure")
                }
            }

            allprojects {
                tasks.addRule("<>Ping") { String name ->
                    if (name.endsWith("Ping") && name.size() == 5) {
                        tasks.create(name, Ping)
                    }
                }
                tasks.addRule("<>FailingPing") { String name ->
                    if (name.endsWith("FailingPing")) {
                        tasks.create(name, FailingPing)
                    }
                }
                tasks.addRule("<>SerialPing") { String name ->
                    if (name.endsWith("SerialPing")) {
                        tasks.create(name, SerialPing)
                    }
                }
            }
        """
        executer.withArgument('--info')
    }

    void withParallelThreads(int threadCount) {
        executer.withArgument("--max-workers=$threadCount")
    }

    def "info is logged when overlapping outputs prevent parallel execution"() {
        given:
        executer.withArgument("-i")
        withParallelThreads(2)

        and:
        buildFile << """
            aPing.outputs.file "dir"
            bPing.outputs.file "dir/file"
        """
        expect:
        GradleHandle handle
        def handleStarter = {  handle = executer.withTasks(":aPing", ":bPing").start() }
        blockingServer.expectConcurrentExecution([":aPing"]) {
            ConcurrentTestUtil.poll(1) {
                assert handle.standardOutput.contains("Cannot execute task :bPing in parallel with task :aPing due to overlapping output: ${file("dir")}")
            }
        }
        blockingServer.expectSerialExecution(":bPing")
        handleStarter.call()
        handle.waitForFinish()
    }

    def "independent tasks from multiple projects execute in parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrentExecution(":a:aPing", ":a:bPing", ":b:aPing")

        run ":a:aPing", ":a:bPing", ":b:aPing"
    }

    def "two tasks with should run after execute in parallel"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            bPing.shouldRunAfter aPing
        """

        then:
        blockingServer.expectConcurrentExecution(":aPing", ":bPing")

        run ":aPing", ":bPing"
    }

    def "two tasks that are dependencies of another task are executed in parallel"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            aPing.dependsOn bPing, cPing
        """

        then:
        blockingServer.expectConcurrentExecution(":bPing", ":cPing")
        blockingServer.expectSerialExecution(":aPing")

        run ":aPing"
    }

    def "task is not executed if one of its dependencies executed in parallel fails"() {
        given:
        withParallelThreads(2)

        when:
        buildFile << """
            aPing.dependsOn bPing, cFailingPing
        """

        then:
        blockingServer.expectConcurrentExecution(":bPing", ":cFailingPing")

        when:
        fails ":aPing"

        then:
        notExecuted(":aPing")
    }

    def "the number of tasks executed in parallel is limited by the number of parallel threads"() {
        given:
        withParallelThreads(2)

        expect:
        blockingServer.expectConcurrentExecution(":aPing", ":bPing")
        blockingServer.expectConcurrentExecution(":cPing", ":dPing")

        run ":aPing", ":bPing", ":cPing", ":dPing"
    }

    def "tasks are run in parallel if there are tasks without async work running in a different project using --parallel"() {
        given:
        executer.withArgument("--parallel")
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrentExecution(":a:aSerialPing", ":b:aPing", ":b:bPing")

        run ":a:aSerialPing", ":b:aPing", ":b:bPing"
    }

    def "tasks are not run in parallel if there are tasks without async work running in a different project without --parallel"() {
        given:
        withParallelThreads(3)

        expect:
        blockingServer.expectConcurrentExecution(":a:aSerialPing")
        blockingServer.expectConcurrentExecution(":b:aPing", ":b:bPing")

        run ":a:aSerialPing", ":b:aPing", ":b:bPing"
    }
}
