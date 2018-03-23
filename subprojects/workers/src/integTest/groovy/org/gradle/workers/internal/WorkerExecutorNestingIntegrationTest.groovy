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

import spock.lang.Timeout
import spock.lang.Unroll

@Timeout(60)
class WorkerExecutorNestingIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    @Unroll
    def "workers with no isolation can spawn more work with #nestedIsolationMode"() {
        buildFile << """
            ${getRunnableWithNesting("IsolationMode.NONE", nestedIsolationMode)}
            task runInWorker(type: NestingWorkerTask)
        """.stripIndent()

        when:
        def gradle = executer.withTasks("runInWorker").start()

        then:
        gradle.waitForFinish()

        and:
        gradle.standardOutput.contains("Hello World")

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
        def gradle = executer.withTasks("runInWorker").start()

        then:
        gradle.waitForFinish()

        and:
        gradle.standardOutput.contains("Hello World")
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
        failure.assertHasCause("Unable to determine argument #0: no service of type interface org.gradle.workers.WorkerExecutor, or value Hello World not assignable to type interface org.gradle.workers.WorkerExecutor")

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
        failure.assertHasCause("Unable to determine argument #0: no service of type interface org.gradle.workers.WorkerExecutor, or value Hello World not assignable to type interface org.gradle.workers.WorkerExecutor")

        where:
        nestedIsolationMode << ISOLATION_MODES
    }

    String getRunnableWithNesting(String isolationMode, String nestedIsolationMode) {
        return """
            import javax.inject.Inject
            import org.gradle.workers.WorkerExecutor

            class FirstLevelRunnable implements Runnable {
            
                WorkerExecutor executor
                String greeting
                
                @Inject
                public FirstLevelRunnable(WorkerExecutor executor, String greeting) {
                    this.executor = executor
                    this.greeting = greeting
                }

                public void run() {
                    executor.submit(SecondLevelRunnable) {
                        isolationMode = $nestedIsolationMode
                        params = [greeting]
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

                @Inject
                NestingWorkerTask(WorkerExecutor executor) {
                    this.executor = executor
                }

                @TaskAction
                public void runInWorker() {
                    executor.submit(FirstLevelRunnable) {
                        isolationMode = $isolationMode
                        params = ["Hello World"]
                    }
                    if (waitForChildren) {
                        executor.await()
                    }
                }
            }
        """.stripIndent()
    }
}
