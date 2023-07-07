/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.internal;

import com.google.common.base.Joiner;
import net.rubygrapefruit.platform.ProcessLauncher;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.operations.CurrentBuildOperationPreservingRunnable;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.shutdown.ShutdownHooks;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;
import static org.gradle.process.internal.util.LongCommandLineDetectionUtil.hasCommandLineExceedMaxLength;
import static org.gradle.process.internal.util.LongCommandLineDetectionUtil.hasCommandLineExceedMaxLengthException;

/**
 * Default implementation for the ExecHandle interface.
 *
 * <h3>State flows</h3>
 *
 * <ul>
 *   <li>INIT -&gt; STARTED -&gt; [SUCCEEDED|FAILED|ABORTED|DETACHED]</li>
 *   <li>INIT -&gt; FAILED</li>
 *   <li>INIT -&gt; STARTED -&gt; DETACHED -&gt; ABORTED</li>
 * </ul>
 *
 * State is controlled on all control methods:
 * <ul>
 * <li>{@link #start()} allowed when state is INIT</li>
 * <li>{@link #abort()} allowed when state is STARTED or DETACHED</li>
 * </ul>
 */
public class DefaultExecHandle implements ExecHandle, ProcessSettings {

    private static final Logger LOGGER = Logging.getLogger(DefaultExecHandle.class);

    private static final Joiner ARGUMENT_JOINER = Joiner.on(' ').useForNull("null");

    private final String displayName;

    /**
     * The working directory of the process.
     */
    private final File directory;

    /**
     * The executable to run.
     */
    private final String command;

    /**
     * Arguments to pass to the executable.
     */
    private final List<String> arguments;

    /**
     * The variables to set in the environment the executable is run in.
     */
    private final Map<String, String> environment;
    private final StreamsHandler outputHandler;
    private final StreamsHandler inputHandler;
    private final boolean redirectErrorStream;
    private final ProcessLauncher processLauncher;
    private int timeoutMillis;
    private boolean daemon;

    /**
     * Lock to guard all mutable state
     */
    private final Lock lock;
    private final Condition stateChanged;

    private final Executor executor;

    /**
     * State of this ExecHandle.
     */
    private ExecHandleState state;

    /**
     * When not null, the runnable that is waiting
     */
    private ExecHandleRunner execHandleRunner;

    private ExecResultImpl execResult;

    private final ListenerBroadcast<ExecHandleListener> broadcast;

    private final ExecHandleShutdownHookAction shutdownHookAction;

    private final BuildCancellationToken buildCancellationToken;

    DefaultExecHandle(String displayName, File directory, String command, List<String> arguments,
                      Map<String, String> environment, StreamsHandler outputHandler, StreamsHandler inputHandler,
                      List<ExecHandleListener> listeners, boolean redirectErrorStream, int timeoutMillis, boolean daemon,
                      Executor executor, BuildCancellationToken buildCancellationToken) {
        this.displayName = displayName;
        this.directory = directory;
        this.command = command;
        this.arguments = arguments;
        this.environment = environment;
        this.outputHandler = outputHandler;
        this.inputHandler = inputHandler;
        this.redirectErrorStream = redirectErrorStream;
        this.timeoutMillis = timeoutMillis;
        this.daemon = daemon;
        this.executor = executor;
        this.lock = new ReentrantLock();
        this.stateChanged = lock.newCondition();
        this.state = ExecHandleState.INIT;
        this.buildCancellationToken = buildCancellationToken;
        processLauncher = NativeServices.getInstance().get(ProcessLauncher.class);
        shutdownHookAction = new ExecHandleShutdownHookAction(this);
        broadcast = new ListenerBroadcast<ExecHandleListener>(ExecHandleListener.class);
        broadcast.addAll(listeners);
    }

    @Override
    public File getDirectory() {
        return directory;
    }

    @Override
    public String getCommand() {
        return command;
    }

    public boolean isDaemon() {
        return daemon;
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public List<String> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    @Override
    public Map<String, String> getEnvironment() {
        return Collections.unmodifiableMap(environment);
    }

    @Override
    public ExecHandleState getState() {
        lock.lock();
        try {
            return state;
        } finally {
            lock.unlock();
        }
    }

    private void setState(ExecHandleState state) {
        lock.lock();
        try {
            LOGGER.debug("Changing state to: {}", state);
            this.state = state;
            this.stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean stateIn(ExecHandleState... states) {
        lock.lock();
        try {
            return Arrays.asList(states).contains(this.state);
        } finally {
            lock.unlock();
        }
    }

    private void setEndStateInfo(ExecHandleState newState, int exitValue, Throwable failureCause) {
        ShutdownHooks.removeShutdownHook(shutdownHookAction);
        buildCancellationToken.removeCallback(shutdownHookAction);
        ExecHandleState currentState;
        lock.lock();
        try {
            currentState = this.state;
        } finally {
            lock.unlock();
        }

        ExecResultImpl newResult = new ExecResultImpl(exitValue, execExceptionFor(failureCause, currentState), displayName);
        if (!currentState.isTerminal() && newState != ExecHandleState.DETACHED) {
            try {
                broadcast.getSource().executionFinished(this, newResult);
            } catch (Exception e) {
                newResult = new ExecResultImpl(exitValue, execExceptionFor(e, currentState), displayName);
            }
        }

        lock.lock();
        try {
            setState(newState);
            this.execResult = newResult;
        } finally {
            lock.unlock();
        }

        LOGGER.debug("Process '{}' finished with exit value {} (state: {})", displayName, exitValue, newState);
    }

    @Nullable
    private ExecException execExceptionFor(Throwable failureCause, ExecHandleState currentState) {
        return failureCause != null
            ? new ExecException(failureMessageFor(failureCause, currentState), failureCause)
            : null;
    }

    private String failureMessageFor(Throwable failureCause, ExecHandleState currentState) {
        if (currentState == ExecHandleState.STARTING) {
            if (hasCommandLineExceedMaxLength(command, arguments) && hasCommandLineExceedMaxLengthException(failureCause)) {
                return format("Process '%s' could not be started because the command line exceed operating system limits.", displayName);
            }
            return format("A problem occurred starting process '%s'", displayName);
        }
        return format("A problem occurred waiting for process '%s' to complete.", displayName);
    }

    @Override
    public ExecHandle start() {
        LOGGER.info("Starting process '{}'. Working directory: {} Command: {} {}",
                displayName, directory, command, ARGUMENT_JOINER.join(arguments));
        lock.lock();
        try {
            if (!stateIn(ExecHandleState.INIT)) {
                throw new IllegalStateException(format("Cannot start process '%s' because it has already been started", displayName));
            }
            setState(ExecHandleState.STARTING);

            broadcast.getSource().beforeExecutionStarted(this);
            execHandleRunner = new ExecHandleRunner(this, new CompositeStreamsHandler(), processLauncher, executor);
            executor.execute(CurrentBuildOperationPreservingRunnable.wrapIfNeeded(execHandleRunner));

            while (stateIn(ExecHandleState.STARTING)) {
                LOGGER.debug("Waiting until process started: {}.", displayName);
                try {
                    stateChanged.await();
                } catch (InterruptedException e) {
                    execHandleRunner.abortProcess();
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }

            if (execResult != null) {
                execResult.rethrowFailure();
            }

            LOGGER.info("Successfully started process '{}'", displayName);
        } finally {
            lock.unlock();
        }
        return this;
    }

    @Override
    public void removeStartupContext() {
        lock.lock();
        try {
            if (!stateIn(ExecHandleState.STARTED)) {
                throw new IllegalStateException(
                    format("Cannot remove start context of process '%s' because it is not in started state", displayName));
            }
            execHandleRunner.removeStartupContext();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void abort() {
        lock.lock();
        try {
            if (stateIn(ExecHandleState.SUCCEEDED, ExecHandleState.FAILED, ExecHandleState.ABORTED)) {
                return;
            }
            if (!stateIn(ExecHandleState.STARTED, ExecHandleState.DETACHED)) {
                throw new IllegalStateException(
                    format("Cannot abort process '%s' because it is not in started or detached state", displayName));
            }
            this.execHandleRunner.abortProcess();
            this.waitForFinish();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ExecResult waitForFinish() {
        lock.lock();
        try {
            while (!state.isTerminal()) {
                try {
                    stateChanged.await();
                } catch (InterruptedException e) {
                    execHandleRunner.abortProcess();
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }

        // At this point:
        // If in daemon mode, the process has started successfully and all streams to the process have been closed
        // If in fork mode, the process has completed and all cleanup has been done
        // In both cases, all asynchronous work for the process has completed and we're done

        return result();
    }

    private ExecResult result() {
        lock.lock();
        try {
            return execResult.rethrowFailure();
        } finally {
            lock.unlock();
        }
    }

    void detached() {
        setEndStateInfo(ExecHandleState.DETACHED, 0, null);
    }

    void started() {
        ShutdownHooks.addShutdownHook(shutdownHookAction);
        buildCancellationToken.addCallback(shutdownHookAction);
        setState(ExecHandleState.STARTED);
        broadcast.getSource().executionStarted(this);
    }

    void finished(int exitCode) {
        if (exitCode != 0) {
            setEndStateInfo(ExecHandleState.FAILED, exitCode, null);
        } else {
            setEndStateInfo(ExecHandleState.SUCCEEDED, 0, null);
        }
    }

    void aborted(int exitCode) {
        if (exitCode == 0) {
            // This can happen on Windows
            exitCode = -1;
        }
        setEndStateInfo(ExecHandleState.ABORTED, exitCode, null);
    }

    void failed(Throwable failureCause) {
        setEndStateInfo(ExecHandleState.FAILED, -1, failureCause);
    }

    @Override
    public void addListener(ExecHandleListener listener) {
        broadcast.add(listener);
    }

    @Override
    public void removeListener(ExecHandleListener listener) {
        broadcast.remove(listener);
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean getRedirectErrorStream() {
        return redirectErrorStream;
    }

    public int getTimeout() {
        return timeoutMillis;
    }

    private static class ExecResultImpl implements ExecResult {
        private final int exitValue;
        private final ExecException failure;
        private final String displayName;

        ExecResultImpl(int exitValue, ExecException failure, String displayName) {
            this.exitValue = exitValue;
            this.failure = failure;
            this.displayName = displayName;
        }

        @Override
        public int getExitValue() {
            return exitValue;
        }

        @Override
        public ExecResult assertNormalExitValue() throws ExecException {
            if (exitValue != 0) {
                throw new ExecException(format("Process '%s' finished with non-zero exit value %d", displayName, exitValue));
            }
            return this;
        }

        @Override
        public ExecResult rethrowFailure() throws ExecException {
            if (failure != null) {
                throw failure;
            }
            return this;
        }

        @Override
        public String toString() {
            return "{exitValue=" + exitValue + ", failure=" + failure + "}";
        }
    }

    private class CompositeStreamsHandler implements StreamsHandler {
        @Override
        public void connectStreams(Process process, String processName, Executor executor) {
            inputHandler.connectStreams(process, processName, executor);
            outputHandler.connectStreams(process, processName, executor);
        }

        @Override
        public void start() {
            inputHandler.start();
            outputHandler.start();
        }

        @Override
        public void removeStartupContext() {
            inputHandler.removeStartupContext();
            outputHandler.removeStartupContext();
        }

        @Override
        public void stop() {
            outputHandler.stop();
            inputHandler.stop();
        }

        @Override
        public void disconnect() {
            outputHandler.disconnect();
            inputHandler.disconnect();
        }
    }
}
