/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.fixtures.executer;

import com.google.common.base.Joiner;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factory;
import org.gradle.internal.io.NullOutputStream;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.AbstractExecHandleBuilder;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleState;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.Map;

import static java.lang.String.format;
import static org.gradle.util.TextUtil.getPlatformLineSeparator;

class ForkingGradleHandle extends OutputScrapingGradleHandle {

    final private Factory<? extends AbstractExecHandleBuilder> execHandleFactory;

    private final OutputCapturer standardOutputCapturer;
    private final OutputCapturer errorOutputCapturer;
    private final Action<ExecutionResult> resultAssertion;
    private final PipedOutputStream stdinPipe;
    private final boolean isDaemon;

    private final DurationMeasurement durationMeasurement;
    private ExecHandle execHandle;

    public ForkingGradleHandle(PipedOutputStream stdinPipe, boolean isDaemon, Action<ExecutionResult> resultAssertion, String outputEncoding, Factory<? extends AbstractExecHandleBuilder> execHandleFactory, DurationMeasurement durationMeasurement) {
        this.resultAssertion = resultAssertion;
        this.execHandleFactory = execHandleFactory;
        this.isDaemon = isDaemon;
        this.stdinPipe = stdinPipe;
        this.durationMeasurement = durationMeasurement;
        this.standardOutputCapturer = outputCapturerFor(System.out, outputEncoding, durationMeasurement);
        this.errorOutputCapturer = outputCapturerFor(System.err, outputEncoding, durationMeasurement);
    }

    private static OutputCapturer outputCapturerFor(PrintStream stream, String outputEncoding, DurationMeasurement durationMeasurement) {
        return new OutputCapturer(durationMeasurement == null ? stream : NullOutputStream.INSTANCE, outputEncoding);
    }

    @Override
    public PipedOutputStream getStdinPipe() {
        return stdinPipe;
    }

    public String getStandardOutput() {
        return standardOutputCapturer.getOutputAsString();
    }

    public String getErrorOutput() {
        return errorOutputCapturer.getOutputAsString();
    }

    public GradleHandle start() {
        if (execHandle != null) {
            throw new IllegalStateException("you have already called start() on this handle");
        }

        execHandle = buildExecHandle();

        printExecHandleSettings();

        execHandle.start();
        if (durationMeasurement != null) {
            durationMeasurement.start();
        }
        return this;
    }

    private ExecHandle buildExecHandle() {
        return execHandleFactory
            .create()
            .setStandardOutput(standardOutputCapturer.getOutputStream())
            .setErrorOutput(errorOutputCapturer.getOutputStream())
            .build();
    }

    private void printExecHandleSettings() {
        Map<String, String> environment = execHandle.getEnvironment();
        println("Starting build with: " + execHandle.getCommand() + " " + Joiner.on(" ").join(execHandle.getArguments()));
        println("Working directory: " + execHandle.getDirectory());
        println("Environment vars:");
        println(format("    JAVA_HOME: %s", environment.get("JAVA_HOME")));
        println(format("    GRADLE_HOME: %s", environment.get("GRADLE_HOME")));
        println(format("    GRADLE_USER_HOME: %s", environment.get("GRADLE_USER_HOME")));
        println(format("    JAVA_OPTS: %s", environment.get("JAVA_OPTS")));
        println(format("    GRADLE_OPTS: %s", environment.get("GRADLE_OPTS")));
    }

    private static void println(String s) {
        System.out.println(s);
    }

    @Override
    public GradleHandle cancel() {
        requireStdinPipeFor("cancel()");

        try {
            stdinPipe.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return this;
    }

    @Override
    public GradleHandle cancelWithEOT() {
        requireStdinPipeFor("cancelWithEOT()");

        try {
            stdinPipe.write(4);
            if (isDaemon) {
                // When running a test in a daemon executer, the input is buffered until a
                // newline char is received
                stdinPipe.write(getPlatformLineSeparator().getBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return this;
    }

    private void requireStdinPipeFor(String method) {
        if (stdinPipe == null) {
            throw new UnsupportedOperationException("Handle must be started using GradleExecuter.withStdinPipe() to use " + method);
        }
    }

    public GradleHandle abort() {
        getExecHandle().abort();
        return this;
    }

    public boolean isRunning() {
        return execHandle != null && execHandle.getState() == ExecHandleState.STARTED;
    }

    private ExecHandle getExecHandle() {
        if (execHandle == null) {
            throw new IllegalStateException("you must call start() before calling this method");
        }
        return execHandle;
    }

    public ExecutionResult waitForFinish() {
        return waitForStop(false);
    }

    public ExecutionFailure waitForFailure() {
        return (ExecutionFailure) waitForStop(true);
    }

    @Override
    public void waitForExit() {
        getExecHandle().waitForFinish().rethrowFailure();
    }

    private ExecutionResult waitForStop(boolean expectFailure) {
        ExecResult execResult = getExecHandle().waitForFinish();
        if (durationMeasurement != null) {
            durationMeasurement.stop();
        }
        execResult.rethrowFailure(); // nop if all ok

        String output = getStandardOutput();
        String error = getErrorOutput();
        boolean didFail = execResult.getExitValue() != 0;
        ExecutionResult executionResult = didFail ? toExecutionFailure(output, error) : toExecutionResult(output, error);

        if (didFail != expectFailure) {
            throw unexpectedBuildFailure(executionResult, execResult, expectFailure, output, error);
        }

        resultAssertion.execute(executionResult);
        return executionResult;
    }

    private UnexpectedBuildFailure unexpectedBuildFailure(ExecutionResult executionResult, ExecResult execResult, boolean expectFailure, String output, String error) {
        ExecHandle execHandle = getExecHandle();
        String message =
            format("Gradle execution %s in %s with: %s %s%nOutput:%n%s%n-----%nError:%n%s%n-----%nExecution result:%n%s%n-----%n",
                expectFailure ? "did not fail" : "failed", execHandle.getDirectory(), execHandle.getCommand(), execHandle.getArguments(), output, error, execResult.toString());
        Exception exception = executionResult instanceof OutputScrapingExecutionFailure ? ((OutputScrapingExecutionFailure) executionResult).getException() : null;
        return exception != null
            ? new UnexpectedBuildFailure(message, exception)
            : new UnexpectedBuildFailure(message);
    }
}
