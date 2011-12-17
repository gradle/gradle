/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.util.UncheckedException;
import org.gradle.api.logging.Logging;

import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;

import org.gradle.launcher.daemon.server.exec.DaemonCommandExecution;

import java.util.Date;
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
public class DaemonStateCoordinator implements Stoppable, AsyncStoppable {

    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DaemonStateCoordinator.class);

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private boolean stopped;
    private long lastActivityAt = -1;
    private DaemonCommandExecution currentCommandExecution;

    private final Runnable onStart;
    private final Runnable onStartCommand;
    private final Runnable onFinishCommand;
    private final Runnable onStop;

    public DaemonStateCoordinator(Runnable onStart, Runnable onStartCommand, Runnable onFinishCommand, Runnable onStop) {
        this.onStart = onStart;
        this.onStartCommand = onStartCommand;
        this.onFinishCommand = onFinishCommand;
        this.onStop = onStop;
    }

    /**
     * Waits until stopped.
     */
    public void awaitStop() {
        lock.lock();
        try {
            while (!isStopped()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.asUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Called once when the daemon is up and ready for connections.
     */
    public void start() {
        lock.lock();
        try {
            updateActivityTimestamp();
            onStart.run();
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
                if (isStopped()) {
                    return true;
                } else if (hasBeenIdleFor(timeout)) {
                    return false;
                }
            
                try {
                    if (!isStarted()) {
                        LOGGER.debug("waiting for daemon to stop or idle timeout, daemon has not yet started, sleeping until then");
                        condition.await();
                    } else if (isBusy()) {
                        LOGGER.debug("waiting for daemon to stop or idle timeout, daemon is busy, sleeping until it finishes");
                        condition.await();
                    } else if (isIdle()) {
                        Date waitUntil = new Date(lastActivityAt + timeout);
                        LOGGER.debug("waiting for daemon to stop or idle timeout, daemon is idle, sleeping until daemon state change or idle timeout at {}", waitUntil);
                        condition.awaitUntil(waitUntil);
                    } else {
                        throw new IllegalStateException("waiting for daemon to stop or idle timeout, daemon has started but is not busy or idle, this shouldn't happen");
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.asUncheckedException(e);
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

    public void stop() {
        lock.lock();
        try {
            LOGGER.debug("Stop requested. The daemon is running a build: " + isBusy());
            stopped = true;
            onStop.run();
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void requestStop() {
        new DefaultExecutorFactory().create("Daemon Async Stop Request").execute(new Runnable() {
            public void run() {
                stop();
            }
        });
    }

    /**
     * Called when the execution of a command begins.
     * <p>
     * If the daemon is busy (i.e. already executing a command), this method will return the existing
     * execution which the caller should be prepared for without considering the given execution to be in progress.
     * If the daemon is idle the return value will be {@code null} and the given execution will be considered in progress.
     */
    public DaemonCommandExecution onStartCommand(DaemonCommandExecution execution) {
        lock.lock();
        try {
            if (currentCommandExecution != null) { // daemon is busy
                /*
                    This is not particularly abnormal as daemon can become busy between a particular client connecting to it and then
                    sending a command. The UpdateDaemonStateAndHandleBusyDaemon command action will send back a DaemonBusy result
                    to the client which will then just try another daemon, making this a non-error condition.
                */
                LOGGER.debug("onStartCommand({}) called while currentCommandExecution = {}", execution, currentCommandExecution);
                return currentCommandExecution;
            } else {
                LOGGER.debug("onStartCommand({}) called after {} mins of idle", execution, getIdleMinutes());
                currentCommandExecution = execution;
                updateActivityTimestamp();
                onStartCommand.run();
                condition.signalAll();
                return null;
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
     */
    public DaemonCommandExecution onFinishCommand() {
        lock.lock();
        try {
            DaemonCommandExecution execution = currentCommandExecution;
            if (execution == null) {
                LOGGER.warn("onFinishCommand() called while currentCommandExecution is null");
            } else {
                LOGGER.debug("onFinishCommand() called while execution = {}", execution);
                currentCommandExecution = null;
                updateActivityTimestamp();
                onFinishCommand.run();
                condition.signalAll();
            }

            return execution;
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
     * The current command execution, or {@code null} if the daemon is idle.
     */
    public DaemonCommandExecution getCurrentCommandExecution() {
        lock.lock();
        try {
            return currentCommandExecution;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Has the daemon started accepting connections.
     */
    public boolean isStarted() {
        return lastActivityAt != -1;
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

    public boolean hasBeenIdleFor(int milliseconds) {
        if (!isStarted()) {
            return false;
        } else {
            return lastActivityAt < (System.currentTimeMillis() - milliseconds);
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isIdle() {
        return isRunning() && currentCommandExecution == null;
    }

    public boolean isBusy() {
        return isRunning() && !isIdle();
    }
    
    public boolean isRunning() {
        return isStarted() && !isStopped();
    }

}