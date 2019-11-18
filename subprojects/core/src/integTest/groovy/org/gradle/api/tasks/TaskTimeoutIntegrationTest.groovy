/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.workers.IsolationMode
import spock.lang.Unroll

class TaskTimeoutIntegrationTest extends AbstractIntegrationSpec {

    private static final TIMEOUT = 500

    @IntegrationTestTimeout(60)
    def "fails when negative timeout is specified"() {
        given:
        buildFile << """
            task broken() {
                doLast {
                    println "Hello"
                }
                timeout = Duration.ofMillis(-1)
            }
            """

        expect:
        fails "broken"
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasCause("Timeout of task ':broken' must be positive, but was -0.001S")
        result.assertNotOutput("Hello")
    }

    @IntegrationTestTimeout(60)
    def "timeout stops long running method call"() {
        given:
        buildFile << """
            task block() {
                doLast {
                    Thread.sleep(60000)
                }
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        fails "block"
        failure.assertHasDescription("Execution failed for task ':block'.")
        failure.assertHasCause("Timeout has been exceeded")
    }

    @IntegrationTestTimeout(60)
    def "other tasks still run after a timeout if --continue is used"() {
        given:
        buildFile << """
            task block() {
                doLast {
                    Thread.sleep(60000)
                }
                timeout = Duration.ofMillis($TIMEOUT)
            }
            
            task foo() {
            }
            """

        expect:
        fails "block", "foo", "--continue"
        result.assertTaskExecuted(":foo")
        failure.assertHasDescription("Execution failed for task ':block'.")
        failure.assertHasCause("Timeout has been exceeded")
    }

    @IntegrationTestTimeout(60)
    @ToBeFixedForInstantExecution
    def "timeout stops long running exec()"() {
        given:
        file('src/main/java/Block.java') << """ 
            import java.util.concurrent.CountDownLatch;
            import java.util.concurrent.TimeUnit;

            public class Block {
                public static void main(String[] args) throws InterruptedException {
                    new CountDownLatch(1).await(90, TimeUnit.SECONDS);
                }
            }
        """
        buildFile << """
            apply plugin: 'java'
            task block(type: JavaExec) {
                classpath = sourceSets.main.output
                main = 'Block'
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        fails "block"
        failure.assertHasDescription("Execution failed for task ':block'.")
        failure.assertHasCause("Timeout has been exceeded")
    }

    @IntegrationTestTimeout(60)
    def "timeout stops long running tests"() {
        given:
        (1..100).each { i ->
            file("src/test/java/Block${i}.java") << """ 
                import java.util.concurrent.CountDownLatch;
                import java.util.concurrent.TimeUnit;
                import org.junit.Test;
    
                public class Block${i} {
                    @Test
                    public void test() throws InterruptedException {
                        new CountDownLatch(1).await(90, TimeUnit.SECONDS);
                    }
                }
            """
        }
        buildFile << """
            apply plugin: 'java'
            ${jcenterRepository()}
            dependencies {
                testImplementation 'junit:junit:4.12'
            }
            test {
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        fails "test"
        failure.assertHasDescription("Execution failed for task ':test'.")
        failure.assertHasCause("Timeout has been exceeded")
    }

    @LeaksFileHandles // TODO https://github.com/gradle/gradle-private/issues/1532
    @IntegrationTestTimeout(60)
    @Unroll
    def "timeout stops long running work items with #isolationMode isolation"() {
        given:
        if (isolationMode == IsolationMode.PROCESS) {
            // worker starting threads can be interrupted during worker startup and cause a 'Could not initialise system classpath' exception.
            // See: https://github.com/gradle/gradle/issues/8699
            executer.withStackTraceChecksDisabled()
        }
        buildFile << """
            import java.util.concurrent.CountDownLatch;
            import java.util.concurrent.TimeUnit;
            import javax.inject.Inject;
            
            task block(type: WorkerTask) {
                timeout = Duration.ofMillis($TIMEOUT)
            }
            
            class WorkerTask extends DefaultTask {

                @Inject
                WorkerExecutor getWorkerExecutor() {
                    throw new UnsupportedOperationException()
                }

                @TaskAction
                void executeTask() {
                    for (int i = 0; i < 100; i++) {
                        workerExecutor.submit(BlockingRunnable) {
                            isolationMode = IsolationMode.$isolationMode
                        }
                    }
                }
            }

            public class BlockingRunnable implements Runnable {
                @Inject
                public BlockingRunnable() {
                }

                public void run() {
                    new CountDownLatch(1).await(90, TimeUnit.SECONDS);
                }
            }
            """

        expect:
        fails "block"
        failure.assertHasDescription("Execution failed for task ':block'.")
        failure.assertHasCause("Timeout has been exceeded")
        if (isolationMode == IsolationMode.PROCESS && failure.output.contains("Caused by:")) {
            assert failure.output.contains("Error occurred during initialization of VM")
        }

        where:
        isolationMode << IsolationMode.values()
    }
}
