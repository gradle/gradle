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

import org.gradle.integtests.fixtures.ProcessFixture
import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.util.internal.TextUtil

class DestroyForkedProcessesIntegrationTest extends DaemonIntegrationSpec {
    private DaemonClientFixture client
    private int daemonLogCheckpoint

    def "forked subprocess tree is destroyed on cancellation"() {
        given:
        def processStartedToken = file("processStarted.txt")
        forkedProcessesCode(processStartedToken)
        def javaPath = TextUtil.normaliseFileSeparators(Jvm.current().javaExecutable.absolutePath)
        // On Windows additional `conhost.exe` processes are spawned as children of java processes
        def expectedDescendantProcesses = OperatingSystem.current().isWindows() ? 6 : 3

        buildFile << """
            plugins {
                id("java-library")
            }

            tasks.register('javaExec', JavaExec) {
                mainClass = 'AppWithChildWithGrandChild'
                classpath = sourceSets.main.output
                environment('JAVA_EXE_PATH', '${javaPath}')
                environment('CLASSPATH', sourceSets.main.output.asPath)
            }
        """

        when:
        startBuild("javaExec", processStartedToken)

        then:
        def processes = childrenAndGrandchildrenOfDaemon()
        processes.size() == expectedDescendantProcesses

        when:
        cancelBuild("javaExec")

        then:
        processes.forEach {
            assert !new ProcessFixture(it as Long).isAlive()
        }
    }

    void forkedProcessesCode(File processStartedToken) {
        file('src/main/java/Block.java') << """
            public class Block {
                public static void main(String[] args) throws java.io.IOException, InterruptedException {
                    new java.io.File("${TextUtil.normaliseFileSeparators(processStartedToken.absolutePath)}").createNewFile();
                    new java.util.concurrent.CountDownLatch(1).await();
                }
            }
        """
        file("src/main/java/AppWithChild.java") << """
            public class AppWithChild {
                public static void main(String[] args) throws java.io.IOException, InterruptedException {
                    String java = System.getenv("JAVA_EXE_PATH");
                    Runtime.getRuntime().exec(new String[]{java, "Block"}).waitFor();
                }
            }
        """
        file("src/main/java/AppWithChildWithGrandChild.java") << """
            public class AppWithChildWithGrandChild {
                public static void main(String[] args) throws java.io.IOException, InterruptedException {
                    String java = System.getenv("JAVA_EXE_PATH");
                    Runtime.getRuntime().exec(new String[]{java, "AppWithChild"}).waitFor();
                }
            }
        """
    }

    private void startBuild(String task, File processStartedToken) {
        executer.withArgument('--debug').withTasks(task)
        client = new DaemonClientFixture(executer.start())
        waitForProcess(processStartedToken)
        daemons.daemon.assertBusy()
    }

    private void cancelBuild(String task) {
        client.kill()
        waitForDaemonLog("Build operation 'Task :$task' completed")
        assert !client.gradleHandle.standardOutput.contains("BUILD FAIL")
        assert !client.gradleHandle.standardOutput.contains("BUILD SUCCESS")
        daemons.daemon.becomesIdle()
    }

    private void waitForProcess(File processStartedToken) {
        ConcurrentTestUtil.poll(60, {
            assert processStartedToken.exists()
        })
    }

    private void waitForDaemonLog(String output) {
        String daemonLogSinceLastCheckpoint = ''
        ConcurrentTestUtil.poll(60, {
            daemonLogSinceLastCheckpoint = daemons.daemon.log.substring(daemonLogCheckpoint)
            assert daemonLogSinceLastCheckpoint.contains(output)
        })

        daemonLogCheckpoint += daemonLogSinceLastCheckpoint.length()
    }

    private List<String> childrenAndGrandchildrenOfDaemon() {
        return new ProcessFixture(daemons.daemon.context.pid).descendants
    }
}
