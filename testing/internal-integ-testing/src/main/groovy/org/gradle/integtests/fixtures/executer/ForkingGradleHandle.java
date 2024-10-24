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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.internal.Factory;
import org.gradle.internal.io.NullOutputStream;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.AbstractExecHandleBuilder;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleState;
import org.gradle.test.fixtures.file.TestFile;

import java.io.IOException;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.String.format;
import static org.gradle.util.internal.TextUtil.getPlatformLineSeparator;

class ForkingGradleHandle extends OutputScrapingGradleHandle {

    final private Factory<? extends AbstractExecHandleBuilder> execHandleFactory;

    private final OutputCapturer standardOutputCapturer;
    private final OutputCapturer errorOutputCapturer;
    private final Action<ExecutionResult> resultAssertion;
    private final PipedOutputStream stdinPipe;
    private final boolean isDaemon;

    private final DurationMeasurement durationMeasurement;
    private final AtomicReference<ExecHandle> execHandleRef = new AtomicReference<>();

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

    @Override
    public String getStandardOutput() {
        return standardOutputCapturer.getOutputAsString();
    }

    @Override
    public String getErrorOutput() {
        return errorOutputCapturer.getOutputAsString();
    }

    public GradleHandle start() {
        ExecHandle execHandle = buildExecHandle();
        if (this.execHandleRef.getAndSet(execHandle) != null) {
            throw new IllegalStateException("you have already called start() on this handle");
        }

        checkDistributionExists();
        printExecHandleSettings();

        execHandle.start();
        if (durationMeasurement != null) {
            durationMeasurement.start();
        }
        return this;
    }

    private void checkDistributionExists() {
        //noinspection ResultOfMethodCallIgnored
        new TestFile(getExecHandle().getCommand()).getParentFile().assertIsDir();
    }

    private ExecHandle buildExecHandle() {
        AbstractExecHandleBuilder builder = execHandleFactory.create();
        assert builder != null;
        builder.getStandardOutput().set(standardOutputCapturer.getOutputStream());
        builder.getErrorOutput().set(errorOutputCapturer.getOutputStream());
        return builder.build();
    }

    private void printExecHandleSettings() {
        ExecHandle execHandle = getExecHandle();
        Map<String, String> environment = execHandle.getEnvironment();
        println("Starting build with: " + execHandle.getCommand() + " " + Joiner.on(" ").join(execHandle.getArguments()));
        println("Working directory: " + execHandle.getDirectory());
        println("Environment vars:");
        println(format("    JAVA_HOME: %s", environment.get("JAVA_HOME")));
        println(format("    GRADLE_HOME: %s", environment.get("GRADLE_HOME")));
        println(format("    GRADLE_USER_HOME: %s", environment.get("GRADLE_USER_HOME")));
        println(format("    JAVA_OPTS: %s", environment.get("JAVA_OPTS")));
        println(format("    GRADLE_OPTS: %s", environment.get("GRADLE_OPTS")));
        String roCache = environment.get(ArtifactCachesProvider.READONLY_CACHE_ENV_VAR);
        if (roCache != null) {
            println(format("    %s: %s", ArtifactCachesProvider.READONLY_CACHE_ENV_VAR, roCache));
        }
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

    @Override
    public GradleHandle abort() {
        getExecHandle().abort();
        return this;
    }

    @Override
    public boolean isRunning() {
        ExecHandle execHandle = this.execHandleRef.get();
        return execHandle != null && execHandle.getState() == ExecHandleState.STARTED;
    }

    private ExecHandle getExecHandle() {
        ExecHandle handle = execHandleRef.get();
        if (handle == null) {
            throw new IllegalStateException("you must call start() before calling this method");
        }
        return handle;
    }

    @Override
    public ExecutionResult waitForFinish() {
        return waitForStop(false);
    }

    @Override
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

        // Exit value is unreliable for determination of process failure.
        // On rare occasions, exitValue == 0 when the process is expected to fail, and the error output indicates failure.
        boolean buildFailed = execResult.getExitValue() != 0 || OutputScrapingExecutionFailure.hasFailure(output);
        ExecutionResult executionResult = buildFailed ? toExecutionFailure(output, error) : toExecutionResult(output, error);

        if (expectFailure && !buildFailed) {
            throw unexpectedBuildStatus(execResult, output, error, "did not fail");
        }
        if (!expectFailure && buildFailed) {
            throw unexpectedBuildStatus(execResult, output, error, "failed");
        }

        resultAssertion.execute(executionResult);
        return executionResult;
    }

    private UnexpectedBuildFailure unexpectedBuildStatus(ExecResult execResult, String output, String error, String status) {
        ExecHandle execHandle = getExecHandle();
        String message =
            format("Gradle execution %s in %s with: %s %s%n"
                    + "Process ExecResult:%n%s%n"
                    + "-----%n"
                    + "Output:%n%s%n"
                    + "-----%n"
                    + "Error:%n%s%n"
                    + "-----%n",
                status, execHandle.getDirectory(), execHandle.getCommand(), execHandle.getArguments(), execResult.toString(), output, error);
        return new UnexpectedBuildFailure(message);
    }
}
