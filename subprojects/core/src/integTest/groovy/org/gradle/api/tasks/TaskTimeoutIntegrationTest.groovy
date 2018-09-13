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
import org.gradle.workers.IsolationMode
import spock.lang.Timeout
import spock.lang.Unroll

class TaskTimeoutIntegrationTest extends AbstractIntegrationSpec {

    private static final TIMEOUT = 500

    @Timeout(30)
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
        failure.assertHasDescription("Timeout of task ':broken' must be positive, but was -0.001S")
        result.assertNotOutput("Hello")
    }

    @Timeout(30)
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
        failure.assertHasDescription("task ':block' exceeded its timeout")
    }

    @Timeout(30)
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
        failure.assertHasDescription("task ':block' exceeded its timeout")
    }

    @Timeout(30)
    def "timeout stops long running exec()"() {
        given:
        file('src/main/java/Block.java') << """ 
            import java.util.concurrent.CountDownLatch;

            public class Block {
                public static void main(String[] args) throws InterruptedException {
                    new CountDownLatch(1).await();
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
        failure.assertHasDescription("task ':block' exceeded its timeout")
    }

    @Timeout(30)
    def "timeout stops long running tests"() {
        given:
        (1..100).each { i ->
            file("src/test/java/Block${i}.java") << """ 
                import java.util.concurrent.CountDownLatch;
                import org.junit.Test;
    
                public class Block${i} {
                    @Test
                    public void test() throws InterruptedException {
                        new CountDownLatch(1).await();
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
        failure.assertHasDescription("task ':test' exceeded its timeout")
    }

    @Timeout(30)
    @Unroll
    def "timeout stops long running work items with #isolationMode isolation"() {
        given:
        buildFile << """
            import java.util.concurrent.CountDownLatch;
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
                    new CountDownLatch(1).await();
                }
            }
            """

        expect:
        fails "block"
        failure.assertHasDescription("task ':block' exceeded its timeout")

        where:
        isolationMode << IsolationMode.values()
    }
}
