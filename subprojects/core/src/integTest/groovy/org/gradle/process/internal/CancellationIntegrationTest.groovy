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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.daemon.DaemonClientFixture
import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Unroll

class CancellationIntegrationTest extends DaemonIntegrationSpec implements DirectoryBuildCacheFixture {
    private static final String START_UP_MESSAGE = "Cancellable task started!"
    private DaemonClientFixture client
    private int daemonLogCheckpoint

    @Unroll
    @UnsupportedWithConfigurationCache(iterationMatchers = ".* project.exec")
    def "can cancel #scenario"() {
        given:
        blockCode()
        buildFile << """
            import java.util.concurrent.CountDownLatch
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

            task javaExec(type: JavaExec) {
                classpath = sourceSets.main.output
                mainClass = 'Block'
            }

            task blockingCustomTask() {
                doLast {
                    System.out.println("$START_UP_MESSAGE")
                    new CountDownLatch(1).await()
                }
            }
        """

        expect:
        assertTaskIsCancellable(task)

        where:
        scenario         | task
        'Exec'           | 'execTask'
        'project.exec'   | 'projectExecTask'
        'JavaExec'       | 'javaExec'
        'blocking tasks' | 'blockingCustomTask'
    }

    @Unroll
    def "task gets rerun after cancellation when buildcache = #buildCacheEnabled and ignoreExitValue = #ignoreExitValue"() {
        given:
        file('outputFile') << ''
        blockCode()
        buildFile << """
            apply plugin: 'java'

            @CacheableTask
            class MyJavaExec extends JavaExec {
                @Input
                String getInput() { "input" }

                @OutputFile
                File getOutputFile() { new java.io.File('${fileToPath(file('outputFile'))}') }
            }

            task exec(type: MyJavaExec) {
                classpath = sourceSets.main.output
                mainClass = 'Block'
                ignoreExitValue = ${ignoreExitValue}
            }
        """

        expect:
        assertTaskGetsRerun('exec', buildCacheEnabled)

        where:
        buildCacheEnabled | ignoreExitValue
        true              | true
        true              | false
        false             | true
        false             | false
    }

    @Unroll
    def "task gets rerun after cancellation when buildcache = #buildCacheEnabled and exceptions ignored = #ignored"() {
        given:
        file('outputFile') << ''
        blockCode()
        buildFile << """
            apply plugin: 'java'

            @CacheableTask
            abstract class MyExec extends DefaultTask {
                @Input
                String getInput() { "input" }

                @OutputFile
                File getOutputFile() { new java.io.File('${fileToPath(file('outputFile'))}') }

                @Inject
                abstract ExecOperations getExecOperations()

                @TaskAction
                void action() {
                    try {
                        def result = execOperations.exec { commandLine '${fileToPath(Jvm.current().javaExecutable)}', '-cp', '${fileToPath(file('build/classes/java/main'))}', 'Block' }
                    } catch (Throwable t) {
                        if(!${ignored}) {
                            throw t
                        }
                    }
                }
            }

            task exec(type: MyExec) {
                dependsOn 'compileJava'
            }
        """

        expect:
        assertTaskGetsRerun('exec', buildCacheEnabled)

        where:
        buildCacheEnabled | ignored
        true              | true
        true              | false
        false             | true
        false             | false
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

    private void startBuild(String task, boolean buildCacheEnabled) {
        executer.withArgument('--debug').withTasks(task)
        if (buildCacheEnabled) {
            executer.withBuildCacheEnabled()
        }

        client = new DaemonClientFixture(executer.start())
        waitForDaemonLog(START_UP_MESSAGE)
        daemons.daemon.assertBusy()
    }

    private void cancelBuild(String task) {
        client.kill()
        waitForDaemonLog("Build operation 'Task :$task' completed")
        assert !client.gradleHandle.standardOutput.contains("BUILD FAIL")
        assert !client.gradleHandle.standardOutput.contains("BUILD SUCCESS")
        daemons.daemon.becomesIdle()
    }

    private void assertTaskGetsRerun(String task, boolean buildCacheEnabled = false) {
        startBuild(task, buildCacheEnabled)
        cancelBuild(task)

        startBuild(task, buildCacheEnabled)
        cancelBuild(task)

        assert daemons.daemons.size() == 1
    }

    private void assertTaskIsCancellable(String task, boolean buildCacheEnabled = false) {
        startBuild(task, buildCacheEnabled)
        cancelBuild(task)

        def build = executer.withTasks("help").withArguments("--debug").start()
        build.waitForFinish()
        assert daemons.daemons.size() == 1
    }

    private void waitForDaemonLog(String output) {
        String daemonLogSinceLastCheckpoint = ''
        ConcurrentTestUtil.poll(120, {
            daemonLogSinceLastCheckpoint = daemons.daemon.log.substring(daemonLogCheckpoint)
            assert daemonLogSinceLastCheckpoint.contains(output)
        })

        daemonLogCheckpoint += daemonLogSinceLastCheckpoint.length()
    }
}
