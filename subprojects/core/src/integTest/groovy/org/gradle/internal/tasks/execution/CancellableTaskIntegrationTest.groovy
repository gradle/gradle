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

package org.gradle.internal.tasks.execution

import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil

class CancellableTaskIntegrationTest extends DaemonIntegrationSpec {

    private static final String START_UP_MESSAGE = "Cancellable task started!"

    def "custom cancellable task can work"() {
        given:
        buildFile << """
            import org.gradle.api.internal.tasks.execution.Cancellable

            class MyCancellableTask extends DefaultTask implements Cancellable {
                Thread taskExecutionThread

                @TaskAction
                void run() {
                    println '$START_UP_MESSAGE'
                    taskExecutionThread = Thread.currentThread()
                    new java.util.concurrent.CountDownLatch(1).await()
                }

                void onCancel() {
                    taskExecutionThread.interrupt()
                }
            }

            task block(type: MyCancellableTask)
        """

        expect:
        taskIsCancellable()
    }

    void taskIsCancellable() {
        def client = new DaemonClientFixture(executer.withArgument("--debug").withTasks("block").start())
        waitFor(START_UP_MESSAGE)
        daemons.daemon.assertBusy()

        client.kill()
        waitFor('BUILD FAILED in')
        daemons.daemon.assertIdle()

        def build = executer.withTasks("tasks").withArguments("--debug").start()
        build.waitForFinish()
        assert daemons.daemons.size() == 1
    }

    def 'daemon is not killed when ctrl-c is pressed during JavaExec task execution'() {
        given:
        buildFile << """
            apply plugin: 'java'

            task block(type: JavaExec) {
                classpath = sourceSets.main.output
                main = 'Block'
            }
        """

        file('src/main/java/Block.java') << """
            public class Block {
                public static void main(String[] args) throws InterruptedException {
                    System.out.println("$START_UP_MESSAGE");
                    new java.util.concurrent.CountDownLatch(1).await();
                }
            }
        """

        expect:
        taskIsCancellable()
    }

    void waitFor(String output) {
        ConcurrentTestUtil.poll {
            assert daemons.daemon.log.contains(output)
        }
    }
}
