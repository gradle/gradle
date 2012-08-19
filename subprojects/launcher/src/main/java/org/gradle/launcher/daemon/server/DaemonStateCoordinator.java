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

package org.gradle.launcher.daemon.server;

import org.gradle.api.logging.Logging;
import org.gradle.internal.Stoppable;
import org.gradle.internal.UncheckedException;
import org.gradle.launcher.daemon.server.exec.DaemonBusyException;
import org.gradle.launcher.daemon.server.exec.DaemonStateControl;
import org.slf4j.Logger;

import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A tool for synchronising the state amongst different threads.
 *
 * This class has no knowledge of the Daemon's internals and is designed to be used internally by the
 * daemon to coordinate itself and allow worker threads to control the daemon's busy/idle status.
 *
 * This is not exposed to clients of the daemon.
 */
public class DaemonStateCoordinator implements Stoppable, DaemonStateControl {
    private static final Logger LOGGER = Logging.getLogger(DaemonStateCoordinator.class);
    private enum State { NotStarted, Running, StopRequested, Stopped }

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private State state = State.NotStarted;
    private long lastActivityAt = -1;
    private String currentCommandExecution;

    private final Executor executor;
    private final Runnable onStart;
    private final Runnable onStartCommand;
    private final Runnable onFinishCommand;
    private final Runnable onStop;
    private final Runnable onStopRequested;

    public DaemonStateCoordinator(Executor executor, Runnable onStart, Runnable onStartCommand, Runnable onFinishCommand, Runnable onStop, Runnable onStopRequested) {
        this.executor = executor;
        this.onStart = onStart;
        this.onStartCommand = onStartCommand;
        this.onFinishCommand = onFinishCommand;
        this.onStop = onStop;
        this.onStopRequested = onStopRequested;
    }

    /**
     * Called once when the daemon is up and ready for connections.
     */
    public void start() {
        lock.lock();
        try {
            if (state != State.NotStarted) {
                throw new IllegalStateException("Cannot start daemon as it has already been started.");
            }
            updateActivityTimestamp();
            onStart.run();
            state = State.Running;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits until stopped, or timeout.
     *
     * @return true if stopped, false if timeout
     */
    public boolean awaitStopOrIdleTimeout(int timeout) {
        lock.lock();
        try {
            LOGGER.debug("waiting for daemon to stop or be idle for {}ms", timeout);
            while (true) {
                try {
                    switch (state) {
                        case NotStarted:
                            LOGGER.debug("Daemon has not yet started, sleeping until state changes.");
                            condition.await();
                            break;
                        case Running:
                            if (isBusy()) {
                                LOGGER.debug("Daemon is busy, sleeping until state changes.");
                                condition.await();
                            } else if (hasBeenIdleFor(timeout)) {
                                LOGGER.debug("Daemon has been idle for requested period.");
                                return false;
                            } else {
                                Date waitUntil = new Date(lastActivityAt + timeout);
                                LOGGER.debug("Daemon is idle, sleeping until state change or idle timeout at {}", waitUntil);
                                condition.awaitUntil(waitUntil);
                            }
                            break;
                        case StopRequested:
                            LOGGER.debug("Daemon is stopping, sleeping until state changes.");
                            condition.await();
                            break;
                        case Stopped:
                            LOGGER.debug("Daemon has stopped.");
                            return true;
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void awaitIdleTimeout(int timeout) throws DaemonStoppedException {
        if (awaitStopOrIdleTimeout(timeout)) {
            throw new DaemonStoppedException(currentCommandExecution);
        }
    }

    public void stopAsSoonAsIdle() {
        lock.lock();
        try {
            LOGGER.debug("Stop as soon as idle requested. The daemon is busy: " + isBusy());
            if (isBusy()) {
                beginStopping();
            } else {
               stop();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Forcibly stops the daemon, even if it is busy.
     *
     * If the daemon is busy and the client is waiting for a response, it may receive “null” from the daemon
     * as the connection may be closed by this method before the result is sent back.
     *
     * @see #stopAsSoonAsIdle()
     */
    public void stop() {
        lock.lock();
        try {
            LOGGER.debug("Stop requested. The daemon is running a build: " + isBusy());
            beginStopping();
            finishStopping();
        } finally {
            lock.unlock();
        }
    }

    private void beginStopping() {
        switch (state) {
            case NotStarted:
                throw new IllegalStateException("This daemon has not been started.");
            case Running:
                onStopRequested.run();
                state = State.StopRequested;
                break;
            case StopRequested:
            case Stopped:
                break;
            default:
                throw new IllegalStateException("Daemon is in unexpected state: " + state);
        }
    }

    private void finishStopping() {
        switch (state) {
            case NotStarted:
            case Running:
                throw new IllegalStateException("This daemon has not been stopped.");
            case StopRequested:
                onStop.run();
                state = State.Stopped;
                condition.signalAll();
                break;
            case Stopped:
                break;
            default:
                throw new IllegalStateException("Daemon is in unexpected state: " + state);
        }
    }

    public void requestStop() {
        lock.lock();
        try {
            beginStopping();
            // not blocking
            executor.execute(new Runnable() {
                public void run() {
                    stop();
                }
            });
        } finally {
            lock.unlock();
        }
    }

    public void runCommand(Runnable command, String commandDisplayName) throws DaemonBusyException {
        onStartCommand(commandDisplayName);
        try {
            command.run();
        } finally {
            onFinishCommand();
        }
    }

    /**
     * Called when the execution of a command begins.
     * <p>
     * If the daemon is busy (i.e. already executing a command), this method will return the existing
     * execution which the caller should be prepared for without considering the given execution to be in progress.
     * If the daemon is idle the return value will be {@code null} and the given execution will be considered in progress.
     */
    private void onStartCommand(String commandDisplayName) {
        lock.lock();
        try {
            switch (state) {
                case NotStarted:
                    throw new IllegalStateException("This daemon has not been started.");
                case StopRequested:
                    throw new IllegalStateException("This daemon is stopping.");
                case Stopped:
                    throw new IllegalStateException("This daemon has stopped.");
            }
            if (currentCommandExecution != null) {
                throw new DaemonBusyException(currentCommandExecution);
            } else {
                LOGGER.debug("onStartCommand({}) called after {} mins of idle", commandDisplayName, getIdleMinutes());
                currentCommandExecution = commandDisplayName;
                updateActivityTimestamp();
                onStartCommand.run();
                condition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called when the execution of a command is complete (or at least the daemon is available for new commands).
     * <p>
     * If the daemon is currently idle, this method will return {@code null}. If it is busy, it will return what was the
     * current execution which will considered to be complete (putting the daemon back in idle state).
     * <p>
     * If {@link #stopAsSoonAsIdle()} was previously called, this method will block while the daemon {@link #stop() stops}
     */
    private void onFinishCommand() {
        lock.lock();
        try {
            String execution = currentCommandExecution;
            LOGGER.debug("onFinishCommand() called while execution = {}", execution);
            currentCommandExecution = null;
            updateActivityTimestamp();
            switch(state) {
                case Running:
                    onFinishCommand.run();
                    condition.signalAll();
                    break;
                case StopRequested:
                case Stopped:
                    stop();
                    break;
                default:
                    throw new IllegalStateException("Daemon is in unexpected state: " + state);
            }
        } finally {
            lock.unlock();
        }
    }

    private void updateActivityTimestamp() {
        long now = System.currentTimeMillis();
        LOGGER.debug("updating lastActivityAt to {}", now);
        lastActivityAt = now;
    }

    /**
     * Has the daemon started accepting connections.
     */
    public boolean isStarted() {
        return state != State.NotStarted;
    }

    public double getIdleMinutes() {
        lock.lock();
        try {
            if (isStarted()) {
                return (System.currentTimeMillis() - lastActivityAt) / 1000 / 60;
            } else {
                return -1;
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean hasBeenIdleFor(int milliseconds) {
        return lastActivityAt < (System.currentTimeMillis() - milliseconds);
    }

    boolean isStopped() {
        return state == State.Stopped;
    }

    boolean isStoppingOrStopped() {
        return state == State.Stopped || state == State.StopRequested;
    }

    boolean isIdle() {
        return currentCommandExecution == null;
    }

    boolean isBusy() {
        return !isIdle();
    }
}