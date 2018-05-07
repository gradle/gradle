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

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Unroll

class CancellableTaskIntegrationTest extends DaemonIntegrationSpec implements DirectoryBuildCacheFixture {
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
        assertTaskIsCancellable()

        where:
        scenario << ['swallowed', 'not swallowed']
    }

    @Unroll
    def "task gets rerun after cancellation when interrupt exception is #scenario and buildcache = #buildCacheEnabled"() {
        given:
        file('outputFile') << ''
        buildFile << """
            import org.gradle.api.execution.Cancellable

            @CacheableTask
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
        assertCancellableTaskGetsRerun(buildCacheEnabled)

        where:
        scenario        | buildCacheEnabled
        'swallowed'     | true
        'swallowed'     | false
        'not swallowed' | true
        'not swallowed' | false
    }

    private void startBuild(boolean buildCacheEnabled = false) {
        executer.withArgument('--debug').withTasks('block')
        if (buildCacheEnabled) {
            executer.withBuildCacheEnabled()
        }

        client = new DaemonClientFixture(executer.start())
        waitForDaemonLog(START_UP_MESSAGE)
        daemons.daemon.assertBusy()
    }

    private void cancelBuild() {
        client.kill()
        waitForDaemonLog('Build cancelled')
        assert !client.gradleHandle.standardOutput.contains('Build cancelled')
        assert !client.gradleHandle.standardOutput.contains('BUILD SUCCESS')
        assert !client.gradleHandle.standardOutput.contains('BUILD FAILED')
        daemons.daemon.assertIdle()
    }

    private void assertCancellableTaskGetsRerun(boolean buildCacheEnabled) {
        startBuild(buildCacheEnabled)
        cancelBuild()

        startBuild(buildCacheEnabled)
        cancelBuild()

        assert daemons.daemons.size() == 1
    }

    private void assertTaskIsCancellable() {
        startBuild()
        cancelBuild()

        def build = executer.withTasks("tasks").withArguments("--debug").start()
        build.waitForFinish()
        assert daemons.daemons.size() == 1
    }

    private void waitForDaemonLog(String output) {
        ConcurrentTestUtil.poll {
            assert daemons.daemon.log.contains(output)
        }
    }
}
