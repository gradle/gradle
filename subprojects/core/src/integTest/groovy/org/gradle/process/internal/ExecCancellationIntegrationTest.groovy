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

package org.gradle.process.internal

import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Unroll

class ExecCancellationIntegrationTest extends DaemonIntegrationSpec implements DirectoryBuildCacheFixture {
    private static final String START_UP_MESSAGE = "Cancellable task started!"
    private DaemonClientFixture client

    @Unroll
    def "can cancel #scenario"() {
        given:
        blockCode()
        buildFile << """
            apply plugin: 'java'
            task execTask(type: Exec) {
                dependsOn 'compileJava'
                commandLine '${fileToPath(Jvm.current().javaExecutable)}', '-cp', '${fileToPath(file('build/classes/java/main'))}', 'Block'
            }
            
            task projectExecTask {
                dependsOn 'compileJava'
                doLast {
                    def result = exec { commandLine '${fileToPath(Jvm.current().javaExecutable)}', '-cp', '${fileToPath(file('build/classes/java/main'))}', 'Block' }
                    assert result.exitValue == 0
                }
            }
        """

        expect:
        assertTaskIsCancellable(task)

        where:
        scenario       | task
        'Exec'         | 'execTask'
        'project.exec' | 'projectExecTask'
    }

    def "can cancel JavaExec"() {
        given:
        blockCode()
        buildFile << """
            apply plugin: 'java'
            task exec(type: JavaExec) {
                classpath = sourceSets.main.output
                main = 'Block'
            }
        """

        expect:
        assertTaskIsCancellable('exec')
    }

    String fileToPath(File file) {
        file.absolutePath.replace('\\', '/')
    }

    void blockCode() {
        file('src/main/java/Block.java') << """ 
            import java.util.concurrent.CountDownLatch;

            public class Block {
                public static void main(String[] args) throws InterruptedException {
                    System.out.println("$START_UP_MESSAGE");
                    new CountDownLatch(1).await();
                }
            }
        """
    }

    private void startBuild(String task) {
        executer.withArgument('--debug').withTasks(task)

        client = new DaemonClientFixture(executer.start())
        waitForDaemonLog(START_UP_MESSAGE)
        daemons.daemon.assertBusy()
    }

    private void cancelBuild() {
        client.kill()
        waitForDaemonLog('BUILD FAILED in')
        daemons.daemon.becomesIdle()
    }

    private void assertTaskIsCancellable(String task) {
        startBuild(task)
        cancelBuild()

        startBuild(task)
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
