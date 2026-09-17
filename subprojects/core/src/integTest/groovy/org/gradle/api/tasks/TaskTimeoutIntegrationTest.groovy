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
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.internal.execution.timeout.impl.DefaultTimeoutHandler
import org.gradle.internal.logging.events.operations.LogEventBuildOperationProgressDetails
import org.gradle.test.fixtures.Flaky
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

import java.time.Duration

@Requires(value = TestExecutionPreconditions.NotNoDaemonExecutor, reason = "https://github.com/gradle/gradle-private/issues/3433")
@IntegrationTestTimeout(60)
class TaskTimeoutIntegrationTest extends AbstractIntegrationSpec {

    private static final TIMEOUT = 500
    private static final ABANDONED_WORK_MILLIS = 60_000

    long postTimeoutCheckFrequencyMs = Duration.ofMinutes(3).toMillis()
    long slowStopLogStacktraceFrequencyMs = Duration.ofMinutes(3).toMillis()

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        executer.beforeExecute {
            [
                (DefaultTimeoutHandler.POST_TIMEOUT_CHECK_FREQUENCY_PROPERTY): postTimeoutCheckFrequencyMs,
                (DefaultTimeoutHandler.SLOW_STOP_LOG_STACKTRACE_FREQUENCY_PROPERTY): slowStopLogStacktraceFrequencyMs
            ].each { k, v ->
                executer.withArgument("-D$k=$v".toString())
            }
        }
    }

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
        2.times {
            fails "broken"
            failure.assertHasDescription("Execution failed for task ':broken' (registered in build file 'build.gradle').")
            failure.assertHasCause("Timeout of task ':broken' must be positive, but was -0.001S")
            result.assertNotOutput("Hello")
        }
    }

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
        2.times {
            fails "block"
            failure.assertHasDescription("Execution failed for task ':block' (registered in build file 'build.gradle').")
            failure.assertHasCause("Timeout has been exceeded")
        }
    }

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
        2.times {
            fails "block", "foo", "--continue"
            result.assertTaskScheduled(":foo")
            failure.assertHasDescription("Execution failed for task ':block' (registered in build file 'build.gradle').")
            failure.assertHasCause("Timeout has been exceeded")
        }
    }

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
                mainClass = 'Block'
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        2.times {
            fails "block"
            failure.assertHasDescription("Execution failed for task ':block' (registered in build file 'build.gradle').")
            failure.assertHasCause("Timeout has been exceeded")
        }
    }

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
            ${mavenCentralRepository()}
            dependencies {
                testImplementation 'junit:junit:4.13'
            }
            test {
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        2.times {
            fails "test"
            failure.assertHasDescription("Execution failed for task ':test' (registered by plugin 'org.gradle.jvm-test-suite').")
            failure.assertHasCause("Timeout has been exceeded")
        }
    }

    @LeaksFileHandles
    // TODO https://github.com/gradle/gradle-private/issues/1532
    @Flaky(because = 'https://github.com/gradle/gradle-private/issues/5086')
    def "timeout stops long running work items with #isolationMode isolation"() {
        given:
        if (isolationMode == 'process') {
            // worker starting threads can be interrupted during worker startup and cause a 'Could not initialise system classpath' exception.
            // See: https://github.com/gradle/gradle/issues/8699
            executer.withStackTraceChecksDisabled()
        }
        buildFile << """
            import java.util.concurrent.CountDownLatch
            import java.util.concurrent.TimeUnit
            import org.gradle.workers.WorkParameters

            task block(type: WorkerTask) {
                timeout = Duration.ofMillis($TIMEOUT)
            }

            abstract class WorkerTask extends DefaultTask {

                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @TaskAction
                void executeTask() {
                    for (int i = 0; i < 100; i++) {
                        workerExecutor.${isolationMode}Isolation().submit(BlockingWorkAction) { }
                    }
                }
            }

            abstract class BlockingWorkAction implements WorkAction<WorkParameters.None> {
                public void execute() {
                    new CountDownLatch(1).await(90, TimeUnit.SECONDS);
                }
            }
            """

        expect:
        2.times {
            fails "block"
            failure.assertHasDescription("Execution failed for task ':block' (registered in build file 'build.gradle').")
            failure.assertHasCause("Timeout has been exceeded")
            if (isolationMode == 'process' && failure.output.contains("Caused by:")) {
                assert failure.output.contains("Error occurred during initialization of VM")
            }
        }

        where:
        isolationMode << ['no', 'classLoader', 'process']
    }


    /**
     * Deterministic reproducer for https://github.com/gradle/gradle-private/issues/5086.
     *
     * When a task times out while a work item is running in a process isolation worker daemon,
     * nothing stops that work item: session teardown calls WorkerDaemonClient.stop(), which asks the
     * worker to stop *after* its current request and then blocks in DefaultExecHandle.waitForFinish()
     * with no timeout. The build therefore blocks for as long as the abandoned work item runs.
     *
     * The ':warmup' task exists purely to fork a worker daemon and return it to the idle pool, so that
     * ':block' reuses a warm daemon and its work item starts immediately. Without that, whether the
     * build hangs depends on a race between worker daemon startup and the task timeout, which is why
     * "timeout stops long running work items with process isolation" is only intermittently red.
     */
    @LeaksFileHandles
    @IntegrationTestTimeout(180)
    def "build does not wait for abandoned process isolation work after a timeout"() {
        given:
        // Worker startup threads can be interrupted during teardown, see https://github.com/gradle/gradle/issues/8699
        executer.withStackTraceChecksDisabled()
        def marker = file("work-started.txt")
        buildFile << """
            import java.util.concurrent.CountDownLatch
            import java.util.concurrent.TimeUnit
            import org.gradle.workers.WorkParameters

            interface BlockingParams extends WorkParameters {
                Property<Long> getBlockMillis()
                Property<String> getMarkerPath()
            }

            abstract class BlockingWorkAction implements WorkAction<BlockingParams> {
                void execute() {
                    long millis = parameters.blockMillis.get()
                    if (millis > 0) {
                        new File(parameters.markerPath.get()).text = "started"
                        new CountDownLatch(1).await(millis, TimeUnit.MILLISECONDS)
                    }
                }
            }

            abstract class WorkerTask extends DefaultTask {

                @Inject
                abstract WorkerExecutor getWorkerExecutor()

                @Internal
                abstract Property<Long> getBlockMillis()

                @Internal
                abstract Property<String> getMarkerPath()

                @TaskAction
                void executeTask() {
                    def millis = blockMillis.get()
                    def path = markerPath.get()
                    workerExecutor.processIsolation().submit(BlockingWorkAction) {
                        it.blockMillis.set(millis)
                        it.markerPath.set(path)
                    }
                    if (millis > 0) {
                        // Wait until the work item is really inside its blocking section, so that the
                        // timeout below cannot fire before the worker has started doing the work.
                        def markerFile = new File(path)
                        def deadline = System.currentTimeMillis() + 60000
                        while (!markerFile.exists() && System.currentTimeMillis() < deadline) {
                            Thread.sleep(50)
                        }
                    }
                }
            }

            tasks.register("warmup", WorkerTask) {
                blockMillis = 0L
                markerPath = layout.projectDirectory.file("unused-marker.txt").asFile.absolutePath
            }

            tasks.register("block", WorkerTask) {
                dependsOn "warmup"
                blockMillis = ${ABANDONED_WORK_MILLIS}L
                markerPath = layout.projectDirectory.file("work-started.txt").asFile.absolutePath
                timeout = Duration.ofSeconds(5)
            }
            """

        when:
        def start = System.nanoTime()
        fails "block"
        def elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis()

        then:
        failure.assertHasDescription("Execution failed for task ':block' (registered in build file 'build.gradle').")
        failure.assertHasCause("Timeout has been exceeded")

        and: "the worker daemon really was executing the work item when the timeout fired"
        marker.exists()

        and: "the build did not wait for the abandoned work item to run to completion"
        assert elapsedMillis < ABANDONED_WORK_MILLIS / 2,
            "Build took ${elapsedMillis}ms, i.e. it waited for the abandoned ${ABANDONED_WORK_MILLIS}ms work item " +
                "instead of stopping the worker daemon executing it."
    }

    def "message is logged when stop is requested"() {
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

        and:
        with(result.groupedOutput.task(":block")) {
            assertOutputContains("Requesting stop of task ':block' as it has exceeded its configured timeout of 500ms.")
        }

        and:
        taskLogging(":block") == [
            "Requesting stop of task ':block' as it has exceeded its configured timeout of 500ms."
        ]
    }

    def "additional logging is emitted when task is slow to stop"() {
        given:
        postTimeoutCheckFrequencyMs = 100
        buildFile << """
            task block() {
                doLast {
                    def startAt = System.nanoTime()
                    while (System.nanoTime() - startAt < 3_000_000_000) {}
                }
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        executer.withArgument("--no-problems-report")
        fails "block"

        and:
        def outputLines = result.groupedOutput.task(":block").output.readLines()
        outputLines[0] == "Requesting stop of task ':block' as it has exceeded its configured timeout of 500ms."
        outputLines[1] == "Timed out task ':block' has not yet stopped."
        outputLines[outputLines.size() - 1] == "Timed out task ':block' has stopped."

        and:
        def logging = taskLogging(":block")
        logging[0] == "Requesting stop of task ':block' as it has exceeded its configured timeout of 500ms."
        logging[1] == "Timed out task ':block' has not yet stopped."
        logging[logging.size() - 1] == "Timed out task ':block' has stopped."
    }

    List<String> taskLogging(String taskPath) {
        def taskExecutionOp = operations.only("Task $taskPath")
        def logging = taskExecutionOp.progress(LogEventBuildOperationProgressDetails)*.details
        def timeoutLogging = logging.findAll { it.category == DefaultTimeoutHandler.name }
        timeoutLogging.collect { it.message } as List<String>
    }

    def "task is re-interrupted until it stops"() {
        given:
        postTimeoutCheckFrequencyMs = 100
        buildFile << """
            task block() {
                doLast {
                    def interruptedCount = 0
                    while (true) {
                        if (Thread.interrupted()) {
                            println "received interrupt"
                            if (++interruptedCount == 3) {
                                return
                            }
                        }
                    }
                }
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        fails "block"

        and:
        with(result.groupedOutput.task(":block")) {
            output.count("received interrupt") == 3
        }
    }

    def "stack trace of task is printed if it is slow to stop"() {
        given:
        executer.withStackTraceChecksDisabled()
        postTimeoutCheckFrequencyMs = 100
        slowStopLogStacktraceFrequencyMs = 100

        buildFile << """
            abstract class BusyWaitTask extends DefaultTask {
                @TaskAction
                void run() {
                    checkpoint("1")
                    checkpoint("2")
                    checkpoint("3")
                }

                private void checkpoint(String id) {
                    long startAt = System.nanoTime()
                    while (System.nanoTime() - startAt < 1_000_000_000) { }
                    println "Checkpoint \$id finished"
                }
            }

            tasks.register("block", BusyWaitTask) {
                timeout = Duration.ofMillis($TIMEOUT)
            }
            """

        expect:
        fails "block"

        and:
        with(result.groupedOutput.task(":block")) {
            def slowStopWarningsCount = output.count("Timed out task ':block' has not yet stopped.")

            def stackTraces = output.findAll(~/(?ms)(?=Timed out task ':block' has not yet stopped).+?(?=^\n)/)
            stackTraces.size() > 1

            // We should not have logged the stacktrace each time we checked if it was still running
            stackTraces.size() < slowStopWarningsCount

            // We should have only logged when it changed
            (1..<stackTraces.size()).each {
                stackTraces[it - 1] != stackTraces[it]
            }
        }
    }
}
