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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.shutdown.ShutdownHookActionRegister;
import org.gradle.process.internal.streams.StreamsHandler;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
 *
 * @author Tom Eyckmans
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
    private int timeoutMillis;

    /**
     * Lock to guard all mutable state
     */
    private final Lock lock;

    private final StoppableExecutor executor;

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
                      List<ExecHandleListener> listeners, boolean redirectErrorStream, int timeoutMillis) {
        this.displayName = displayName;
        this.directory = directory;
        this.command = command;
        this.arguments = arguments;
        this.environment = environment;
        this.streamsHandler = streamsHandler;
        this.redirectErrorStream = redirectErrorStream;
        this.timeoutMillis = timeoutMillis;
        this.lock = new ReentrantLock();
        this.state = ExecHandleState.INIT;
        executor = new DefaultExecutorFactory().create(String.format("Run %s", displayName));
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
            this.state = state;
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

    private void setEndStateInfo(ExecHandleState newState, int exitCode, Throwable failureCause) {
        ShutdownHookActionRegister.removeAction(shutdownHookAction);

        ExecResultImpl result;
        ExecHandleState currentState;
        lock.lock();
        try {
            currentState = this.state;
            ExecException wrappedException = null;
            if (failureCause != null) {
                if (currentState == ExecHandleState.STARTING) {
                    wrappedException = new ExecException(String.format("A problem occurred starting %s.",
                            displayName), failureCause);
                } else {
                    wrappedException = new ExecException(String.format(
                            "A problem occurred waiting for %s to complete.", displayName), failureCause);
                }
            }
            setState(newState);
            execResult = new ExecResultImpl(exitCode, wrappedException);
            result = execResult;
        } finally {
            lock.unlock();
        }

        LOGGER.debug("Process: {} was: {}; is now: {}; Result: {}", displayName, currentState, newState, execResult);

        if (currentState != ExecHandleState.DETACHED && newState != ExecHandleState.DETACHED) {
            broadcast.getSource().executionFinished(this, result);
        }
        executor.requestStop();
    }

    public ExecHandle start() {
        lock.lock();
        try {
            ProcessParentingInitializer.intitialize();
            if (!stateIn(ExecHandleState.INIT)) {
                throw new IllegalStateException("already started!");
            }
            setState(ExecHandleState.STARTING);

            execHandleRunner = new ExecHandleRunner(this, streamsHandler);
            execHandleRunner.start();

            if (execResult != null) {
                execResult.rethrowFailure();
            }

            LOGGER.debug("Started {}.", displayName);
        } finally {
            lock.unlock();
        }
        return this;
    }

    public void abort() {
        lock.lock();
        try {
            if (state == ExecHandleState.SUCCEEDED) {
                return;
            }
            if (!stateIn(ExecHandleState.STARTED, ExecHandleState.DETACHED)) {
                throw new IllegalStateException("not in started or detached state!");
            }
            this.execHandleRunner.abortProcess();
        } finally {
            lock.unlock();
        }
    }

    public ExecResult waitForFinish() {
        lock.lock();
        try {
            if (stateIn(ExecHandleState.SUCCEEDED, ExecHandleState.ABORTED, ExecHandleState.FAILED)) {
                return result();
            }
        } finally {
            lock.unlock();
        }
        execHandleRunner.waitForFinish();
        executor.stop();

        return result();
    }

    private ExecResult result() {
        lock.lock();
        try {
            execResult.rethrowFailure();
            return execResult;
        } finally {
            lock.unlock();
        }
    }

    public DetachResult detach() {
        execHandleRunner.waitUntilStreamsHandled();
        executor.stop();
        lock.lock();
        try {
            return new DetachResultImpl(execResult);
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
            // This can happen on windows
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

    private class ExecResultImpl implements ExecResult {
        private final int exitValue;
        private final ExecException failure;

        public ExecResultImpl(int exitValue, ExecException failure) {
            this.exitValue = exitValue;
            this.failure = failure;
        }

        public int getExitValue() {
            return exitValue;
        }

        public ExecResult assertNormalExitValue() throws ExecException {
            if (exitValue != 0) {
                throw new ExecException(String.format("%s finished with (non-zero) exit value %d.", StringUtils.capitalize(displayName), exitValue));
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

    public static class DetachResultImpl implements DetachResult {
        private final ExecResult execResult;

        public DetachResultImpl(ExecResult execResult) {
            this.execResult = execResult;
        }

        public ExecResult getExecResult() {
            return execResult;
        }

        public boolean isProcessCompleted() {
            return execResult!=null;
        }
    }
}
