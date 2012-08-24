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
import org.gradle.launcher.daemon.server.exec.DaemonStateControl;
import org.gradle.launcher.daemon.server.exec.DaemonUnavailableException;
import org.slf4j.Logger;

import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A tool for synchronising the state amongst different threads.
 *
 * This class has no knowledge of the Daemon's internals and is designed to be used internally by the daemon to coordinate itself and allow worker threads to control the daemon's busy/idle status.
 *
 * This is not exposed to clients of the daemon.
 */
public class DaemonStateCoordinator implements Stoppable, DaemonStateControl {
    private static final Logger LOGGER = Logging.getLogger(DaemonStateCoordinator.class);

    private enum State {Running, StopRequested, Stopped, Broken}

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private State state = State.Running;
    private long lastActivityAt = -1;
    private String currentCommandExecution;

    private final Executor executor;
    private final Runnable onStartCommand;
    private final Runnable onFinishCommand;
    private final Runnable onStop;
    private final Runnable onStopRequested;

    public DaemonStateCoordinator(Executor executor, Runnable onStartCommand, Runnable onFinishCommand, Runnable onStop, Runnable onStopRequested) {
        this.executor = executor;
        this.onStartCommand = onStartCommand;
        this.onFinishCommand = onFinishCommand;
        this.onStop = onStop;
        this.onStopRequested = onStopRequested;
        updateActivityTimestamp();
    }

    private void setState(State state) {
        this.state = state;
        condition.signalAll();
    }

    private boolean awaitStop(long timeoutMs) {
        lock.lock();
        try {
            LOGGER.debug("waiting for daemon to stop or be idle for {}ms", timeoutMs);
            while (true) {
                try {
                    switch (state) {
                        case Running:
                            if (isBusy()) {
                                LOGGER.debug("Daemon is busy, sleeping until state changes.");
                                condition.await();
                            } else if (hasBeenIdleFor(timeoutMs)) {
                                LOGGER.debug("Daemon has been idle for requested period.");
                                stop();
                                return false;
                            } else {
                                Date waitUntil = new Date(lastActivityAt + timeoutMs);
                                LOGGER.debug("Daemon is idle, sleeping until state change or idle timeout at {}", waitUntil);
                                condition.awaitUntil(waitUntil);
                            }
                            break;
                        case Broken:
                            throw new IllegalStateException("This daemon is in a broken state.");
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

    public void stopOnIdleTimeout(int timeout, TimeUnit timeoutUnits) throws DaemonStoppedException {
        if (awaitStop(timeoutUnits.toMillis(timeout))) {
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
     * If the daemon is busy and the client is waiting for a response, it may receive “null” from the daemon as the connection may be closed by this method before the result is sent back.
     *
     * @see #stopAsSoonAsIdle()
     */
    public void stop() {
        lock.lock();
        try {
            LOGGER.debug("Stop requested. The daemon is running a build: " + isBusy());
            try {
                beginStopping();
            } finally {
                finishStopping();
            }
        } finally {
            lock.unlock();
        }
    }

    private void beginStopping() {
        switch (state) {
            case Running:
            case Broken:
                try {
                    onStopRequested.run();
                } finally {
                    setState(State.StopRequested);
                }
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
            case Running:
            case Broken:
                throw new IllegalStateException("This daemon has not been stopped.");
            case StopRequested:
                try {
                    onStop.run();
                } finally {
                    setState(State.Stopped);
                }
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
            try {
                beginStopping();
            } finally {
                // not blocking
                executor.execute(new Runnable() {
                    public void run() {
                        stop();
                    }
                });
            }
        } finally {
            lock.unlock();
        }
    }

    public void runCommand(Runnable command, String commandDisplayName) throws DaemonUnavailableException {
        onStartCommand(commandDisplayName);
        try {
            command.run();
        } finally {
            onFinishCommand();
        }
    }

    private void onStartCommand(String commandDisplayName) {
        lock.lock();
        try {
            switch (state) {
                case Broken:
                    throw new DaemonUnavailableException("This daemon is in a broken state and will stop.");
                case StopRequested:
                    throw new DaemonUnavailableException("This daemon is currently stopping.");
                case Stopped:
                    throw new DaemonUnavailableException("This daemon has stopped.");
            }
            if (currentCommandExecution != null) {
                throw new DaemonUnavailableException(String.format("This daemon is currently executing: %s", currentCommandExecution));
            } else {
                LOGGER.debug("onStartCommand({}) called after {} minutes of idle", commandDisplayName, getIdleMinutes());
                currentCommandExecution = commandDisplayName;
                updateActivityTimestamp();
                try {
                    onStartCommand.run();
                    condition.signalAll();
                } catch (Throwable throwable) {
                    setState(State.Broken);
                    throw UncheckedException.throwAsUncheckedException(throwable);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void onFinishCommand() {
        lock.lock();
        try {
            String execution = currentCommandExecution;
            LOGGER.debug("onFinishCommand() called while execution = {}", execution);
            currentCommandExecution = null;
            updateActivityTimestamp();
            switch (state) {
                case Running:
                    try {
                        onFinishCommand.run();
                        condition.signalAll();
                    } catch (Throwable throwable) {
                        setState(State.Broken);
                        throw UncheckedException.throwAsUncheckedException(throwable);
                    }
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

    private double getIdleMinutes() {
        lock.lock();
        try {
            return (System.currentTimeMillis() - lastActivityAt) / 1000 / 60;
        } finally {
            lock.unlock();
        }
    }

    private boolean hasBeenIdleFor(long milliseconds) {
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