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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.operations.BuildOperationIdentifierPreservingRunnable;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.shutdown.ShutdownHookActionRegister;
import org.gradle.process.internal.streams.StreamsHandler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.String.format;

/**
 * Default implementation for the ExecHandle interface.
 *
 * <h3>State flows</h3>
 *
 * <ul>
 *   <li>INIT -> STARTED -> [SUCCEEDED|FAILED|ABORTED|DETACHED]</li>
 *   <li>INIT -> FAILED</li>
 *   <li>INIT -> STARTED -> DETACHED -> ABORTED</li>
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
    private final StreamsHandler streamsHandler;
    private final boolean redirectErrorStream;
    private final ProcessLauncher processLauncher;
    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();
    private int timeoutMillis;
    private boolean daemon;

    /**
     * Lock to guard all mutable state
     */
    private final Lock lock;
    private final Condition stateChanged;

    private final ManagedExecutor executor;

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

    DefaultExecHandle(String displayName, File directory, String command, List<String> arguments,
                      Map<String, String> environment, StreamsHandler streamsHandler,
                      List<ExecHandleListener> listeners, boolean redirectErrorStream, int timeoutMillis, boolean daemon) {
        this.displayName = displayName;
        this.directory = directory;
        this.command = command;
        this.arguments = arguments;
        this.environment = environment;
        this.streamsHandler = streamsHandler;
        this.redirectErrorStream = redirectErrorStream;
        this.timeoutMillis = timeoutMillis;
        this.daemon = daemon;
        this.lock = new ReentrantLock();
        this.stateChanged = lock.newCondition();
        this.state = ExecHandleState.INIT;
        executor = executorFactory.create(format("Run %s", displayName));
        processLauncher = NativeServices.getInstance().get(ProcessLauncher.class);
        shutdownHookAction = new ExecHandleShutdownHookAction(this);
        broadcast = new ListenerBroadcast<ExecHandleListener>(ExecHandleListener.class);
        broadcast.addAll(listeners);
    }

    public File getDirectory() {
        return directory;
    }

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

    public List<String> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public Map<String, String> getEnvironment() {
        return Collections.unmodifiableMap(environment);
    }

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
        ShutdownHookActionRegister.removeAction(shutdownHookAction);

        ExecHandleState currentState;
        lock.lock();
        try {
            currentState = this.state;
            setState(newState);
            ExecResultImpl newResult = new ExecResultImpl(exitValue, execExceptionFor(failureCause, currentState), displayName);
            if (execResult != null) {
                String message = "Attempted to overwrite exec result: " + execResult + " -> " + newResult;
                throw execExceptionFor(new RuntimeException(message), currentState);
            }
            this.execResult = newResult;
        } finally {
            lock.unlock();
        }

        LOGGER.debug("Process '{}' finished with exit value {} (state: {})", displayName, exitValue, newState);

        if (currentState != ExecHandleState.DETACHED && newState != ExecHandleState.DETACHED) {
            broadcast.getSource().executionFinished(this, execResult);
        }
        executor.requestStop();
    }

    @Nullable
    private ExecException execExceptionFor(Throwable failureCause, ExecHandleState currentState) {
        return failureCause != null
            ? new ExecException(failureMessageFor(currentState), failureCause)
            : null;
    }

    private String failureMessageFor(ExecHandleState currentState) {
        return currentState == ExecHandleState.STARTING
            ? format("A problem occurred starting process '%s'", displayName)
            : format("A problem occurred waiting for process '%s' to complete.", displayName);
    }

    public ExecHandle start() {
        LOGGER.info("Starting process '{}'. Working directory: {} Command: {}",
                displayName, directory, command + ' ' + Joiner.on(' ').useForNull("null").join(arguments));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Environment for process '{}': {}", displayName, environment);
        }
        lock.lock();
        try {
            if (!stateIn(ExecHandleState.INIT)) {
                throw new IllegalStateException(format("Cannot start process '%s' because it has already been started", displayName));
            }
            setState(ExecHandleState.STARTING);

            execHandleRunner = new ExecHandleRunner(this, streamsHandler, processLauncher, executorFactory);
            executor.execute(new BuildOperationIdentifierPreservingRunnable(execHandleRunner));

            while (stateIn(ExecHandleState.STARTING)) {
                LOGGER.debug("Waiting until process started: {}.", displayName);
                try {
                    stateChanged.await();
                } catch (InterruptedException e) {
                    //ok, wrapping up
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

    public ExecResult waitForFinish() {
        lock.lock();
        try {
            while (!stateIn(ExecHandleState.SUCCEEDED, ExecHandleState.ABORTED, ExecHandleState.FAILED, ExecHandleState.DETACHED)) {
                try {
                    stateChanged.await();
                } catch (InterruptedException e) {
                    //ok, wrapping up...
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }

        executor.stop();

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
        ShutdownHookActionRegister.addAction(shutdownHookAction);
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

    public void addListener(ExecHandleListener listener) {
        broadcast.add(listener);
    }

    public void removeListener(ExecHandleListener listener) {
        broadcast.remove(listener);
    }

    public String getDisplayName() {
        return displayName;
    }

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

        public int getExitValue() {
            return exitValue;
        }

        public ExecResult assertNormalExitValue() throws ExecException {
            if (exitValue != 0) {
                throw new ExecException(format("Process '%s' finished with non-zero exit value %d", displayName, exitValue));
            }
            return this;
        }

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
}
