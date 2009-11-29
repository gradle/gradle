/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.exec;

import org.gradle.util.ThreadUtils;
import org.gradle.util.shutdown.ShutdownHookActionRegister;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/**
 * Default implementation for the ExecHandle interface.
 *
 * <h3>State flows</h3>
 *
 * <p>The ExecHandle has very strict state control.
 * The following state flows are allowed:</p>
 *
 * Normal state flow:
 * <ul><li>INIT -> STARTED -> SUCCEEDED</li></ul>
 * Failure state flows:
 * <ul>
 * <li>INIT -> FAILED</li>
 * <li>INIT -> STARTED -> FAILED</li>
 * </ul>
 * Aborted state flow:
 * <ul><li>INIT -> STARTED -> ABORTED</li></ul>
 *
 * State is controlled on all control methods:
 * <ul>
 * <li>{@link #start()} can only be called when the state is NOT {@link ExecHandleState#STARTED}</li>
 * <li>{@link #abort()} can only be called when the state is {@link ExecHandleState#STARTED}</li>
 * <li>{@link #startAndWaitForFinish()} can only be called when the state is NOT {@link ExecHandleState#STARTED}</li> 
 * </ul>
 *
 * @author Tom Eyckmans
 */
public class DefaultExecHandle implements ExecHandle {
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
     * The exit code of the executable when it terminates normally.
     */
    private final int normalTerminationExitCode;
    /**
     * The variables to set in the environment the executable is run in.
     */
    private final Map<String, String> environment;
    /**
     * Time in ms to sleep the 'main' Thread that is waiting for the external process to be terminated. Note that this
     * timeout is only used when the {@link Process#waitFor} method is interrupted so it's use very limited.
     */
    private final long keepWaitingTimeout;

    /**
     * The output handle to pass the standard output of the external process to.
     */
    private final ExecOutputHandle standardOutputHandle;
    /**
     * The output handle to pass the error output of the external process to.
     */
    private final ExecOutputHandle errorOutputHandle;

    /**
     * Lock to guard all mutable state
     */
    private final Lock lock;

    private final Condition stateChange;

    /**
     * State of this ExecHandle.
     */
    private ExecHandleState state;

    /**
     * When not null, the runnable that is waiting
     */
    private ExecHandleRunner execHandleRunner;
    private ExecutorService threadPool;

    private int exitCode;
    private Throwable failureCause;

    private final ExecHandleNotifierFactory notifierFactory;
    private final List<ExecHandleListener> listeners = new CopyOnWriteArrayList<ExecHandleListener>();

    private ExecHandleShutdownHookAction shutdownHookAction;

    DefaultExecHandle(File directory, String command, List<?> arguments, int normalTerminationExitCode,
                      Map<String, String> environment, long keepWaitingTimeout, ExecOutputHandle standardOutputHandle,
                      ExecOutputHandle errorOutputHandle, ExecHandleNotifierFactory notifierFactory,
                      List<ExecHandleListener> listeners) {
        this.directory = directory;
        this.command = command;
        this.arguments = new ArrayList<String>();
        for (Object objectArgument : arguments) { // to handle GStrings! otherwise ClassCassExceptions may occur.
            if (objectArgument != null) {
                this.arguments.add(objectArgument.toString());
            }
        }
        this.normalTerminationExitCode = normalTerminationExitCode;
        this.environment = environment;
        this.keepWaitingTimeout = keepWaitingTimeout;
        this.standardOutputHandle = standardOutputHandle;
        this.errorOutputHandle = errorOutputHandle;
        this.lock = new ReentrantLock();
        this.stateChange = lock.newCondition();
        this.state = ExecHandleState.INIT;
        this.notifierFactory = notifierFactory;
        if (listeners != null && !listeners.isEmpty()) {
            this.listeners.addAll(listeners);
        }
    }

    public File getDirectory() {
        return directory;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArguments() {
        return Collections.unmodifiableList(arguments);
    }

    public Map<String, String> getEnvironment() {
        return Collections.unmodifiableMap(environment);
    }

    public long getKeepWaitingTimeout() {
        return keepWaitingTimeout;
    }

    public ExecOutputHandle getStandardOutputHandle() {
        return standardOutputHandle;
    }

    public ExecOutputHandle getErrorOutputHandle() {
        return errorOutputHandle;
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
            stateChange.signalAll();
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

    public int getNormalTerminationExitCode() {
        return normalTerminationExitCode;
    }

    public int getExitCode() {
        lock.lock();
        try {
            if (!stateIn(ExecHandleState.SUCCEEDED, ExecHandleState.FAILED)) {
                throw new IllegalStateException("not in succeeded or failed state!");
            }
            return exitCode;
        } finally {
            lock.unlock();
        }
    }

    public Throwable getFailureCause() {
        lock.lock();
        try {
            if (!stateIn(ExecHandleState.FAILED)) {
                throw new IllegalStateException("not in failed state!");
            }
            return failureCause;
        } finally {
            lock.unlock();
        }
    }

    private void setEndStateInfo(ExecHandleState state, int exitCode, Throwable failureCause) {
        lock.lock();
        try {
            setState(state);
            this.exitCode = exitCode;
            this.failureCause = failureCause;
        } finally {
            lock.unlock();
        }
    }

    public void start() {
        lock.lock();
        try {
            if (!stateIn(ExecHandleState.INIT)) {
                throw new IllegalStateException("already started!");
            }
            setState(ExecHandleState.STARTING);

            exitCode = -1;
            failureCause = null;

            threadPool = Executors.newFixedThreadPool(3);
            execHandleRunner = new ExecHandleRunner(this, threadPool);

            threadPool.execute(execHandleRunner);

            while (getState() == ExecHandleState.STARTING) {
                try {
                    stateChange.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void abort() {
        lock.lock();
        try {
            if (!stateIn(ExecHandleState.STARTED)) {
                throw new IllegalStateException("not in started state!");
            }
            this.execHandleRunner.stopWaiting();
        } finally {
            lock.unlock();
        }
    }

    public ExecHandleState waitForFinish() {
        ThreadUtils.awaitTermination(threadPool);
        return getState();
    }

    private void shutdownThreadPool() {
        ThreadUtils.run(new Runnable() {

            public void run() {
                ThreadUtils.shutdown(threadPool);
            }
        });
    }

    public ExecHandleState startAndWaitForFinish() {
        start();
        waitForFinish();
        return getState();
    }

    void started() {
        shutdownHookAction = new ExecHandleShutdownHookAction(this);
        ShutdownHookActionRegister.addShutdownHookAction(shutdownHookAction);

        setState(ExecHandleState.STARTED);
        ThreadUtils.run(notifierFactory.createStartedNotifier(this));
    }

    void finished(int exitCode) {
        ShutdownHookActionRegister.removeShutdownHookAction(shutdownHookAction);

        if (exitCode != normalTerminationExitCode) {
            setEndStateInfo(ExecHandleState.FAILED, exitCode, new RuntimeException(
                    "exitCode(" + exitCode + ") != " + normalTerminationExitCode + "!"));
            shutdownThreadPool();
            ThreadUtils.run(notifierFactory.createFailedNotifier(this));
        } else {
            setEndStateInfo(ExecHandleState.SUCCEEDED, 0, null);
            shutdownThreadPool();
            ThreadUtils.run(notifierFactory.createSucceededNotifier(this));
        }
    }

    void aborted() {
        ShutdownHookActionRegister.removeShutdownHookAction(shutdownHookAction);

        setState(ExecHandleState.ABORTED);
        shutdownThreadPool();
        ThreadUtils.run(notifierFactory.createAbortedNotifier(this));
    }

    void failed(Throwable failureCause) {
        ShutdownHookActionRegister.removeShutdownHookAction(shutdownHookAction);

        setEndStateInfo(ExecHandleState.FAILED, -1, failureCause);
        shutdownThreadPool();
        ThreadUtils.run(notifierFactory.createFailedNotifier(this));
    }

    public void addListeners(ExecHandleListener... listeners) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners == null!");
        }
        this.listeners.addAll(Arrays.asList(listeners));
    }

    public void removeListeners(ExecHandleListener... listeners) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners == null!");
        }
        this.listeners.removeAll(Arrays.asList(listeners));
    }

    public List<ExecHandleListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }
}
