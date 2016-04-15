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
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.io.output.TeeOutputStream;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.AbstractExecHandleBuilder;
import org.gradle.process.internal.ExecHandle;
import org.gradle.process.internal.ExecHandleState;
import org.gradle.util.TextUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;

class ForkingGradleHandle extends OutputScrapingGradleHandle {
    final private Factory<? extends AbstractExecHandleBuilder> execHandleFactory;

    final private ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
    final private ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
    private final Action<ExecutionResult> resultAssertion;
    private final PipedOutputStream stdinPipe;
    private final boolean isDaemon;

    private ExecHandle execHandle;
    private final String outputEncoding;

    public ForkingGradleHandle(PipedOutputStream stdinPipe, boolean isDaemon, Action<ExecutionResult> resultAssertion, String outputEncoding, Factory<? extends AbstractExecHandleBuilder> execHandleFactory) {
        this.resultAssertion = resultAssertion;
        this.execHandleFactory = execHandleFactory;
        this.outputEncoding = outputEncoding;
        this.isDaemon = isDaemon;
        this.stdinPipe = stdinPipe;
    }

    @Override
    public PipedOutputStream getStdinPipe() {
        return stdinPipe;
    }

    public String getStandardOutput() {
        try {
            return standardOutput.toString(outputEncoding);
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public String getErrorOutput() {
        try {
            return errorOutput.toString(outputEncoding);
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public GradleHandle start() {
        if (execHandle != null) {
            throw new IllegalStateException("you have already called start() on this handle");
        }

        AbstractExecHandleBuilder execBuilder = execHandleFactory.create();
        execBuilder.setStandardOutput(new CloseShieldOutputStream(new TeeOutputStream(System.out, standardOutput)));
        execBuilder.setErrorOutput(new CloseShieldOutputStream(new TeeOutputStream(System.err, errorOutput)));
        execHandle = execBuilder.build();

        System.out.println("Starting build with: " + execHandle.getCommand() + " " + Joiner.on(" ").join(execHandle.getArguments()));
        System.out.println("Working directory: " + execHandle.getDirectory());
        System.out.println("Environment vars:");
        System.out.println(String.format("    JAVA_HOME: %s", execHandle.getEnvironment().get("JAVA_HOME")));
        System.out.println(String.format("    GRADLE_HOME: %s", execHandle.getEnvironment().get("GRADLE_HOME")));
        System.out.println(String.format("    GRADLE_USER_HOME: %s", execHandle.getEnvironment().get("GRADLE_USER_HOME")));
        System.out.println(String.format("    JAVA_OPTS: %s", execHandle.getEnvironment().get("JAVA_OPTS")));
        System.out.println(String.format("    GRADLE_OPTS: %s", execHandle.getEnvironment().get("GRADLE_OPTS")));

        execHandle.start();

        return this;
    }

    @Override
    public GradleHandle cancel() {
        if (stdinPipe == null) {
            throw new UnsupportedOperationException("Handle must be started using GradleExecuter.withStdinPipe() to use cancel()");
        }

        try {
            stdinPipe.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return this;
    }

    @Override
    public GradleHandle cancelWithEOT() {
        if (stdinPipe == null) {
            throw new UnsupportedOperationException("Handle must be started using GradleExecuter.withStdinPipe() to use cancelwithEOT()");
        }

        try {
            stdinPipe.write(4);
            if (isDaemon) {
                // When running a test in a daemon executer, the input is buffered until a
                // newline char is received
                stdinPipe.write(TextUtil.toPlatformLineSeparators("\n").getBytes());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return this;
    }

    public GradleHandle abort() {
        getExecHandle().abort();
        return this;
    }

    public boolean isRunning() {
        return execHandle != null && execHandle.getState() == ExecHandleState.STARTED;
    }

    protected ExecHandle getExecHandle() {
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

    protected ExecutionResult waitForStop(boolean expectFailure) {
        ExecHandle execHandle = getExecHandle();
        ExecResult execResult = execHandle.waitForFinish();
        execResult.rethrowFailure(); // nop if all ok

        String output = getStandardOutput();
        String error = getErrorOutput();

        boolean didFail = execResult.getExitValue() != 0;
        if (didFail != expectFailure) {
            String message = String.format("Gradle execution %s in %s with: %s %s%nOutput:%n%s%n-----%nError:%n%s%n-----%n",
                expectFailure ? "did not fail" : "failed", execHandle.getDirectory(), execHandle.getCommand(), execHandle.getArguments(), output, error);
            throw new UnexpectedBuildFailure(message);
        }

        ExecutionResult executionResult = expectFailure ? toExecutionFailure(output, error) : toExecutionResult(output, error);
        resultAssertion.execute(executionResult);
        return executionResult;
    }
}
