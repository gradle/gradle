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

import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.work.DefaultConditionalExecutionQueue
import spock.lang.Unroll

import static org.gradle.workers.fixtures.WorkerExecutorFixture.ISOLATION_MODES

@IntegrationTestTimeout(120)
class WorkerExecutorNestingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    @Unroll
    def "workers with no isolation can spawn more work with #nestedIsolationMode"() {
        buildFile << """
            ${getRunnableWithNesting("IsolationMode.NONE", nestedIsolationMode)}
            task runInWorker(type: NestingWorkerTask)
        """.stripIndent()

        when:
        succeeds("runInWorker")

        then:
        outputContains("Hello World")

        where:
        nestedIsolationMode << ISOLATION_MODES
    }

    def "workers with no isolation can wait on spawned work"() {
        buildFile << """
            ${getRunnableWithNesting("IsolationMode.NONE", "IsolationMode.NONE")}
            task runInWorker(type: NestingWorkerTask) {
                waitForChildren = true 
            }
        """.stripIndent()

        when:
        succeeds("runInWorker")

        then:
        result.groupedOutput.task(':runInWorker').output.contains("Hello World")
    }

    def "workers with no isolation can spawn more than max workers items of work"() {
        def maxWorkers = 4
        buildFile << """
            ${getRunnableWithNesting("IsolationMode.NONE", "IsolationMode.NONE")}
            task runInWorker(type: NestingWorkerTask) {
                submissions = ${maxWorkers * 2}
                childSubmissions = ${maxWorkers}
            }
        """.stripIndent()

        when:
        executer.withArguments("--max-workers=${maxWorkers}")
        succeeds("runInWorker")

        then:
        result.groupedOutput.task(':runInWorker').output.contains("Hello World")
    }

    def "workers with no isolation can spawn and wait for more than max workers items of work"() {
        def maxWorkers = 4
        buildFile << """
            ${getRunnableWithNesting("IsolationMode.NONE", "IsolationMode.NONE")}
            task runInWorker(type: NestingWorkerTask) {
                waitForChildren = true 
                submissions = ${maxWorkers * 2}
                childSubmissions = ${maxWorkers}
            }
        """.stripIndent()

        when:
        executer.withArguments("--max-workers=${maxWorkers}")
        succeeds("runInWorker")

        then:
        result.groupedOutput.task(':runInWorker').output.contains("Hello World")
    }

    /*
     * This is not intended, but current behavior. We'll need to find a way to pass the service
     * registry across the classloader isolation barrier.
     */
    @Unroll
    def "workers with classpath isolation cannot spawn more work with #nestedIsolationMode"() {
        buildFile << """
            ${getRunnableWithNesting("IsolationMode.CLASSLOADER", nestedIsolationMode)}
            task runInWorker(type: NestingWorkerTask)
        """.stripIndent()

        expect:
        fails("runInWorker")

        and:
        failure.assertHasCause("Could not create an instance of type FirstLevelRunnable.")
        failure.assertHasCause("Unable to determine constructor argument #1: value 'Hello World' is not assignable to interface org.gradle.workers.WorkerExecutor, or no service of type interface org.gradle.workers.WorkerExecutor")

        where:
        nestedIsolationMode << ISOLATION_MODES
    }

    /*
     * Ideally this would be possible, but it would require coordination between workers and the daemon
     * to figure out who is allowed to schedule more work without violating the max-workers setting.
     */
    @Unroll
    def "workers with process isolation cannot spawn more work with #nestedIsolationMode"() {
        buildFile << """
            ${getRunnableWithNesting("IsolationMode.PROCESS", nestedIsolationMode)}
            task runInWorker(type: NestingWorkerTask)
        """.stripIndent()

        expect:
        fails("runInWorker")

        and:
        failure.assertHasCause("Could not create an instance of type FirstLevelRunnable.")
        failure.assertHasCause("Unable to determine constructor argument #1: value 'Hello World' is not assignable to interface org.gradle.workers.WorkerExecutor, or no service of type interface org.gradle.workers.WorkerExecutor")

        where:
        nestedIsolationMode << ISOLATION_MODES
    }

    def "does not leave more than max-workers threads running when work items submit more work"() {
        def maxWorkers = 4

        buildFile << """
            ${getRunnableWithNesting("IsolationMode.NONE", "IsolationMode.NONE")}
            task runInWorker(type: NestingWorkerTask) {
                submissions = ${maxWorkers * 2}
                childSubmissions = ${maxWorkers * 10}

                doLast {
                    def timeout = System.currentTimeMillis() + (${DefaultConditionalExecutionQueue.KEEP_ALIVE_TIME_MS} * 3)
                    
                    // Let the keep-alive time on the thread pool expire
                    sleep(${DefaultConditionalExecutionQueue.KEEP_ALIVE_TIME_MS})

                    def executorThreads = getWorkerExecutorThreads()
                    while(System.currentTimeMillis() < timeout) {
                        if (executorThreads.size() <= ${maxWorkers}) {
                            break
                        }
                        sleep 100
                        executorThreads = getWorkerExecutorThreads()
                    }
                        
                    println "\\nWorker Executor threads:"
                    executorThreads.each { println it }
                    
                    // Ensure that we don't leave any threads lying around
                    assert executorThreads.size() <= ${maxWorkers}
                }
            }

            def getWorkerExecutorThreads() {
                def threadGroup = Thread.currentThread().threadGroup
                def threads = new Thread[threadGroup.activeCount()]
                threadGroup.enumerate(threads)                     
                return threads.findAll { it?.name?.startsWith("${WorkerExecutionQueueFactory.QUEUE_DISPLAY_NAME}") } 
            }
        """.stripIndent()

        when:
        executer.withArguments("--max-workers=${maxWorkers}")
        succeeds("runInWorker")

        then:
        result.groupedOutput.task(':runInWorker').output.contains("Hello World")
    }

    String getRunnableWithNesting(String isolationMode, String nestedIsolationMode) {
        return """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor

            class FirstLevelRunnable implements Runnable {
            
                WorkerExecutor executor
                String greeting
                int childSubmissions
                
                @Inject
                public FirstLevelRunnable(WorkerExecutor executor, String greeting, int childSubmissions) {
                    this.executor = executor
                    this.greeting = greeting
                    this.childSubmissions = childSubmissions
                }

                public void run() {
                    childSubmissions.times {
                        executor.submit(SecondLevelRunnable) {
                            isolationMode = $nestedIsolationMode
                            params = [greeting]
                        }
                    }
                }
            }

            class SecondLevelRunnable implements Runnable {
                
                String greeting

                @Inject
                public SecondLevelRunnable(String greeting) {
                    this.greeting = greeting
                }

                public void run() {
                    System.out.println(greeting)
                }
            }

            class NestingWorkerTask extends DefaultTask {

                WorkerExecutor executor
                boolean waitForChildren = false
                int submissions = 1
                int childSubmissions = 1

                @Inject
                NestingWorkerTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                public void runInWorker() {
                    submissions.times {
                        executor.submit(FirstLevelRunnable) {
                            isolationMode = $isolationMode
                            params = ["Hello World", childSubmissions]
                        }
                    }
                    if (waitForChildren) {
                        executor.await()
                    }
                }
            }
        """.stripIndent()
    }
}
