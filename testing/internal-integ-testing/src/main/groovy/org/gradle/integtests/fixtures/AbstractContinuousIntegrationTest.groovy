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

package org.gradle.integtests.fixtures


import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import org.gradle.integtests.fixtures.timeout.JavaProcessStackTracesMonitor
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.junit.Assume

import static org.gradle.integtests.fixtures.WaitAtEndOfBuildFixture.buildLogicForEndOfBuildWait
import static org.gradle.integtests.fixtures.WaitAtEndOfBuildFixture.buildLogicForMinimumBuildTime

abstract class AbstractContinuousIntegrationTest extends AbstractIntegrationSpec {

    private static final int WAIT_FOR_WATCHING_TIMEOUT_SECONDS = 60
    private static final int WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS = 20
    private static final boolean OS_IS_WINDOWS = OperatingSystem.current().isWindows()
    private static final String CHANGE_DETECTED_OUTPUT = "Change detected, executing build..."
    private static final String WAITING_FOR_CHANGES_OUTPUT = "Waiting for changes to input files..."

    GradleHandle gradle

    private int standardOutputBuildMarker = 0
    private int errorOutputBuildMarker = 0

    int buildTimeout = WAIT_FOR_WATCHING_TIMEOUT_SECONDS
    boolean killToStop
    boolean withoutContinuousArg
    List<ExecutionResult> results = []

    void turnOnDebug() {
        executer.startBuildProcessInDebugger(true)
        buildTimeout *= 100
    }

    def cleanup() {
        stopGradle()
        if (OperatingSystem.current().isWindows()) {
            // needs delay to release file handles
            sleep(500L)
        }
    }

    def setup() {
        Assume.assumeFalse("Continuous build doesn't work with --no-daemon", GradleContextualExecuter.noDaemon)
        executer.beforeExecute {
            def initScript = file("init.gradle")
            initScript.text = buildLogicForMinimumBuildTime(minimumBuildTimeMillis)
            withArgument("-I").withArgument(initScript.absolutePath)
        }
    }

    protected int getMinimumBuildTimeMillis() {
        2000
    }

    protected void withoutContinuousBuild() {
        withoutContinuousArg = true
    }

    def waitAtEndOfBuildForQuietPeriod(def quietPeriodMillis) {
        // Make sure the build lasts long enough for events to propagate
        // Needs to be longer than the quiet period configured
        int sleepPeriod = quietPeriodMillis * 2
        buildFile << buildLogicForEndOfBuildWait(sleepPeriod)
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        runBuild(tasks)
        waitForBuild()
        throwOnBuildFailure()
        result
    }

    protected ExecutionResult buildTriggeredAndSucceeded() {
        if (!gradle.isRunning()) {
            throw new UnexpectedBuildFailure("Gradle has exited")
        }
        waitForBuild()
        throwOnBuildFailure()
        result
    }

    private void throwOnBuildFailure() {
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("""build was expected to succeed but failed:
-- STDOUT --
${result.output}
-- STDOUT --
-- STDERR --
${result.error}
-- STDERR --
""")
        }
    }

    ExecutionFailure fails(String... tasks) {
        runBuild(tasks)
        waitForBuild()
        return extractFailure()
    }

    ExecutionFailure buildTriggeredAndFailed() {
        if (!gradle.isRunning()) {
            throw new UnexpectedBuildFailure("Gradle has exited")
        }
        waitForBuild()
        return extractFailure()
    }

    private ExecutionFailure extractFailure() {
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void runBuild(String... tasks) {
        if (!tasks) {
            throw new IllegalArgumentException("tasks must be specified")
        }
        stopGradle()
        standardOutputBuildMarker = 0
        errorOutputBuildMarker = 0
        executer.withStdinPipe()
            .withTasks(tasks)
            .withForceInteractive(true)
            .withArgument("--full-stacktrace")
        if (!withoutContinuousArg) {
            executer.withArgument("--continuous")
        }
        gradle = executer.start()
    }

    protected OutputStream getStdinPipe() {
        gradle.stdinPipe
    }

    protected void waitForBuild() {
        if (gradle == null) {
            throw new UnexpectedBuildFailure("Gradle never started")
        }

        def lastOutput = buildOutputSoFar()
        def lastLength = lastOutput.size()
        def lastActivity = monotonicClockMillis()
        int endOfBuildReached = 0
        int startIndex = 0

        while (gradle.isRunning() && monotonicClockMillis() - lastActivity < (buildTimeout * 1000)) {
            sleep 100
            lastOutput = buildOutputSoFar()
            if (lastOutput.size() != lastLength) {
                lastActivity = monotonicClockMillis()
                lastLength = lastOutput.size()
                // wait for quiet period in output before detecting build ending
                continue
            }

            int changeDetectedIndex = lastOutput.lastIndexOf(CHANGE_DETECTED_OUTPUT)
            if (changeDetectedIndex > startIndex) {
                startIndex = changeDetectedIndex + CHANGE_DETECTED_OUTPUT.length()
                endOfBuildReached = 0
            }
            if (lastOutput.length() > startIndex && lastOutput.indexOf(WAITING_FOR_CHANGES_OUTPUT, startIndex) > -1) {
                if (endOfBuildReached++ > 1) {
                    // must reach end of build twice before breaking out of loop
                    break
                } else {
                    // wait extra period
                    sleep 100
                }
            }
        }
        if (gradle.isRunning() && !endOfBuildReached) {
            new JavaProcessStackTracesMonitor(temporaryFolder.getTestDirectory()).printAllStackTracesByJstack()
            throw new RuntimeException("""Timeout waiting for build to complete. Output:
$lastOutput

Error:
${gradle.errorOutput}

Look for additional thread dump files in the following folder: $temporaryFolder
""")
        }

        def out = buildOutputSoFar()
        def err = gradle.errorOutput.substring(errorOutputBuildMarker)
        standardOutputBuildMarker = gradle.standardOutput.length()
        errorOutputBuildMarker = gradle.errorOutput.length()

        parseResults(out, err)
        result = results.last()
    }

    private OutputScrapingExecutionResult createExecutionResult(String out, String err) {
        OutputScrapingExecutionResult.from(out, err)
    }

    void parseResults(String out, String err) {
        if (!out) {
            results << createExecutionResult(out, err)
            return
        }
        int startPos = 0
        int endPos = findEndIndexOfCurrentBuild(out, startPos)
        while (startPos < out.length()) {
            if (endPos == -1) {
                endPos = out.length()
            }
            results << createExecutionResult(out.substring(startPos, endPos), err)
            startPos = endPos
            endPos = findEndIndexOfCurrentBuild(out, startPos)
        }
    }

    private int findEndIndexOfCurrentBuild(String out, int startIndex) {
        int waitingForChangesIndex = out.indexOf(WAITING_FOR_CHANGES_OUTPUT, startIndex)
        if (waitingForChangesIndex == -1) {
            return -1
        }
        def waitingForChangesEndIndex = waitingForChangesIndex + WAITING_FOR_CHANGES_OUTPUT.length()
        def newLine = "\n"
        int endOfLineIndex = out.indexOf(newLine, waitingForChangesEndIndex)
        if (endOfLineIndex == -1) {
            return waitingForChangesEndIndex
        }
        endOfLineIndex = endOfLineIndex + newLine.length()
        // if no new build was started, assume that this was the last build and include all output in it
        int nextBuildStart = out.indexOf(CHANGE_DETECTED_OUTPUT, endOfLineIndex)
        if (nextBuildStart == -1) {
            return out.length()
        } else {
            return endOfLineIndex
        }
    }

    private long monotonicClockMillis() {
        System.nanoTime() / 1000000L
    }

    void stopGradle() {
        if (gradle && gradle.isRunning()) {
            if (killToStop) {
                gradle.abort()
            } else {
                gradle.cancel()
                try {
                    waitForNotRunning()
                } finally {
                    if (gradle.running) {
                        gradle.abort()
                    }
                }
            }
        }
    }

    void noBuildTriggered(int waitSeconds = 3) {
        // TODO - change this general strategy to positively detect changes we are ignoring instead of asserting that a build doesn't happen in some time frame
        try {
            ConcurrentTestUtil.poll(waitSeconds, 0.5) {
                // force the poll to continue while there is no output
                assert !buildOutputSoFar().empty
            }
            // if we get here it means that there was build output at some point while polling
            throw new UnexpectedBuildStartedException("Expected build not to start, but started with output: " + buildOutputSoFar())
        } catch (AssertionError ignored) {
            // ok, what we want
        }
        assert gradle.isRunning()
        assert !output.contains("Exiting continuous build")
    }

    // should be private, but is accessed by closures in this class
    protected String buildOutputSoFar() {
        gradle.standardOutput.substring(standardOutputBuildMarker)
    }

    void cancelsAndExits() {
        waitForNotRunning()
        assert buildOutputSoFar().contains("Build cancelled.")
    }

    void doesntExit() {
        try {
            waitForNotRunning()
            assert gradle.running
        } catch (AssertionError ignore) {

        }
    }

    void waitForNotRunning() {
        ConcurrentTestUtil.poll(WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS) {
            assert !gradle.running
        }
    }

    void sendEOT() {
        gradle.cancelWithEOT()
    }

    void waitBeforeModification(File file) {
        long waitMillis = 100L
        if (OS_IS_WINDOWS && file.exists()) {
            // ensure that file modification time changes on windows
            long fileAge = System.currentTimeMillis() - file.lastModified()
            if (fileAge > 0L && fileAge < 900L) {
                waitMillis = 1000L - fileAge
            }
        }
        sleep(waitMillis)
    }

    void update(File file, String text) {
        waitBeforeModification(file)
        file.text = text
    }

    private static class UnexpectedBuildStartedException extends Exception {
        UnexpectedBuildStartedException(String message) {
            super(message)
        }
    }
}
