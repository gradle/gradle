/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous

import com.google.common.util.concurrent.SimpleTimeLimiter
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.*
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.streams.SafeStreams
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import org.spockframework.runtime.SpockTimeoutError
import spock.util.concurrent.PollingConditions

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

@Requires(TestPrecondition.JDK7_OR_LATER)
abstract public class AbstractContinuousIntegrationTest extends AbstractIntegrationSpec {

    private static final int WAIT_FOR_WATCHING_TIMEOUT_SECONDS = 30
    private static final int WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS = 10
    private static final byte[] KEY_CODE_CTRL_D_BYTE_ARRAY = [(byte) 4] as byte[]

    GradleHandle gradle

    private int standardOutputBuildMarker = 0
    private int errorOutputBuildMarker = 0

    int buildTimeout = WAIT_FOR_WATCHING_TIMEOUT_SECONDS
    int shutdownTimeout = WAIT_FOR_SHUTDOWN_TIMEOUT_SECONDS
    boolean expectBuildFailure = false

    PrintStream stdinPipe

    public void turnOnDebug() {
        executer.withDebug(true)
        executer.withArgument("--no-daemon")
        buildTimeout *= 100
        shutdownTimeout *= 100
    }

    public void cleanupWhileTestFilesExist() {
        stopGradle()
        if (OperatingSystem.current().isWindows()) {
            // needs delay to release file handles
            sleep(500L)
        }
    }

    @Override
    protected ExecutionResult succeeds(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        }
        if (gradle == null) {
            throw new UnexpectedBuildFailure("Gradle never started")
        }
        waitForBuild()
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        result
    }

    ExecutionFailure fails(String... tasks) {
        if (tasks) {
            runBuild(tasks)
        }
        waitForBuild()
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void runBuild(String... tasks) {
        stopGradle()
        standardOutputBuildMarker = 0
        errorOutputBuildMarker = 0

        PipedInputStream emulatedSystemIn = new PipedInputStream()
        executer.withStdIn(emulatedSystemIn)
        stdinPipe = new PrintStream(new PipedOutputStream(emulatedSystemIn), true)

        gradle = executer.withTasks(tasks).withArgument("--continuous").start()
    }

    private void waitForBuild() {
        // TODO: change this to 'tick' on any output change rather than waiting for the hold build to complete
        //       to be more adaptable to slow build environments without using huge timeouts
        new PollingConditions(initialDelay: 0.5).within(buildTimeout) {
            if (gradle.isRunning()) {
                assert buildOutputSoFar().endsWith(TextUtil.toPlatformLineSeparators("Waiting for changes to input files of tasks... (ctrl+c to exit)\n"))
            }
        }

        def out = buildOutputSoFar()
        def err = gradle.errorOutput.substring(errorOutputBuildMarker)
        standardOutputBuildMarker = gradle.standardOutput.length()
        errorOutputBuildMarker = gradle.errorOutput.length()

        //noinspection GroovyConditionalWithIdenticalBranches
        result = out.contains("BUILD SUCCESSFUL") ? new OutputScrapingExecutionResult(out, err) : new OutputScrapingExecutionFailure(out, err)
    }

    void stopGradle() {
        if (gradle && gradle.isRunning()) {
            emulateCtrlD()
            assert new SimpleTimeLimiter().callWithTimeout(new Callable() {
                @Override
                Boolean call() throws Exception {
                    try {
                        if (expectBuildFailure) {
                            gradle.waitForFailure()
                        } else {
                            gradle.waitForFinish()
                        }
                    } finally {
                        stdinPipe?.close()
                        stdinPipe = null
                        executer.withStdIn(SafeStreams.emptyInput())
                    }
                    return Boolean.TRUE
                }
            }, shutdownTimeout, TimeUnit.SECONDS, false)
        }
    }

    void emulateCtrlD() {
        if (stdinPipe) {
            stdinPipe << KEY_CODE_CTRL_D_BYTE_ARRAY
        }
    }

    void noBuildTriggered(int waitSeconds = 3) {
        // TODO - change this general strategy to positively detect changes we are ignoring instead of asserting that a build doesn't happen in some time frame
        try {
            new PollingConditions(initialDelay: 0.5).within(waitSeconds) {
                assert !buildOutputSoFar().empty
            }
            throw new AssertionError("Expected build not to start, but started with output: " + buildOutputSoFar())
        } catch (SpockTimeoutError e) {
            // ok, what we want
        }
    }

    // should be private, but is accessed by closures in this class
    protected String buildOutputSoFar() {
        gradle.standardOutput.substring(standardOutputBuildMarker)
    }

    void expectOutput(double waitSeconds = 3.0, Closure<?> checkOutput) {
        new PollingConditions(initialDelay: 0.5).within(waitSeconds) {
            assert checkOutput(buildOutputSoFar())
        }
    }
}
