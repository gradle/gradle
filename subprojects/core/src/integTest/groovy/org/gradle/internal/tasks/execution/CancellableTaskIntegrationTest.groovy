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
import spock.lang.Unroll

class CancellableTaskIntegrationTest extends DaemonIntegrationSpec {
    private static final String START_UP_MESSAGE = "Cancellable task started!"
    private DaemonClientFixture client

    @Unroll
    def "custom cancellable task can work when interrupt exception is #scenario"() {
        given:
        buildFile << """
            import org.gradle.api.execution.Cancellable

            class MyCancellableTask extends DefaultTask implements Cancellable {
                volatile Thread taskExecutionThread

                @TaskAction
                void run() {
                    println '$START_UP_MESSAGE'
                    taskExecutionThread = Thread.currentThread()
                    try {
                        new java.util.concurrent.CountDownLatch(1).await()
                    } catch (InterruptedException e) {
                        ${scenario == 'swallowed' ? '' : 'throw e'}
                    }
                }

                void cancel() {
                    taskExecutionThread.interrupt()
                }
            }

            task block(type: MyCancellableTask)
        """

        expect:
        taskIsCancellable()

        where:
        scenario << ['swallowed', 'not swallowed']
    }

    @Unroll
    def "task gets rerun after cancellation when interrupt exception is #scenario"() {
        given:
        file('outputFile') << ''
        buildFile << """
            import org.gradle.api.execution.Cancellable

            class MyCancellableTask extends DefaultTask implements Cancellable {
                volatile Thread taskExecutionThread
                
                @Input
                String getInput(){ "input" }

                @OutputFile
                File getOutputFile(){ new java.io.File(project.rootDir, 'outputFile') }

                @TaskAction
                void run() {
                    println '$START_UP_MESSAGE'
                    taskExecutionThread = Thread.currentThread()
                    try {
                        new java.util.concurrent.CountDownLatch(1).await()
                    } catch (InterruptedException e) {
                        ${scenario == 'swallowed' ? '' : 'throw e'}
                    }
                }

                void cancel() {
                    taskExecutionThread.interrupt()
                }
            }

            task block(type: MyCancellableTask)
        """

        expect:
        cancellableTaskGetsRerun()

        where:
        scenario << ['swallowed', 'not swallowed']
    }

    private void startBuild() {
        client = new DaemonClientFixture(executer.withArgument("--debug").withTasks("block").start())
        waitFor(START_UP_MESSAGE)
        daemons.daemon.assertBusy()
    }

    private void cancelBuild() {
        client.kill()
        waitFor('Build cancelled')
        daemons.daemon.assertIdle()
    }

    private void cancellableTaskGetsRerun() {
        startBuild()
        cancelBuild()

        startBuild()
        cancelBuild()

        assert daemons.daemons.size() == 1
    }

    private void taskIsCancellable() {
        startBuild()
        cancelBuild()

        def build = executer.withTasks("tasks").withArguments("--debug").start()
        build.waitForFinish()
        assert daemons.daemons.size() == 1
    }

    private void waitFor(String output) {
        ConcurrentTestUtil.poll {
            assert daemons.daemon.log.contains(output)
        }
    }
}
