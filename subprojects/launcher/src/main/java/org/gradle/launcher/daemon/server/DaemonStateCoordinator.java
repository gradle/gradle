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

import org.gradle.api.logging.Logging;
import org.gradle.internal.Stoppable;
import org.gradle.internal.UncheckedException;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecution;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;

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
public class DaemonStateCoordinator implements Stoppable {

    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DaemonStateCoordinator.class);

    private final Lock lock = new ReentrantLock();
    Condition condition = lock.newCondition();

    private boolean stoppingOrStopped;
    private boolean stopped;
    private long lastActivityAt = -1;
    private DaemonCommandExecution currentCommandExecution;

    private final Runnable onStart;
    private final Runnable onStartCommand;
    private final Runnable onFinishCommand;
    private final Runnable onStop;
    private final Runnable onStopRequested;

    public DaemonStateCoordinator(Runnable onStart, Runnable onStartCommand, Runnable onFinishCommand, Runnable onStop, Runnable onStopRequested) {
        this.onStart = onStart;
        this.onStartCommand = onStartCommand;
        this.onFinishCommand = onFinishCommand;
        this.onStop = onStop;
        this.onStopRequested = onStopRequested;
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
                    throw UncheckedException.throwAsUncheckedException(e);
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

    /**
     * Perform a stop, but wait until the daemon is idle to cut any open connections.
     *
     * The daemon will be removed from the registry upon calling this regardless of whether it is busy or not.
     * If it is idle, this method will block until the daemon fully stops.
     *
     * If the daemon is busy, this method will return after removing the daemon from the registry but before the
     * daemon is fully stopped. In this case, the daemon will stop as soon as {@link #onFinishCommand()} is called.
     */
    public void stopAsSoonAsIdle() {
        lock.lock();
        try {
            LOGGER.debug("Stop as soon as idle requested. The daemon is busy: " + isBusy());
            if (isBusy()) {
                onStopRequested.run();
                stoppingOrStopped = true;
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
            if (!isStoppingOrStopped()) {
                onStopRequested.run();
            }
            stoppingOrStopped = true;
            onStop.run();
            stopped = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    Runnable asyncStop = new Runnable() {
        public void run() {
            new DefaultExecutorFactory().create("Daemon Async Stop Request").execute(new Runnable() {
                public void run() {
                    stop();
                }
            });
        }
    };

    /**
     * @return returns false if the daemon was already requested to stop
     */
    public boolean requestStop() {
        lock.lock();
        try {
            if (stoppingOrStopped) {
                return false;
            }
            stoppingOrStopped = true;
            onStopRequested.run(); //blocking
            asyncStop.run(); //not blocking
            return true;
        } finally {
            lock.unlock();
        }
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
     * <p>
     * If {@link #stopAsSoonAsIdle()} was previously called, this method will block while the daemon {@link #stop() stops}
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
                if (isStoppingOrStopped()) {
                    stop();
                } else {
                    onFinishCommand.run();
                    condition.signalAll();
                }
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

    public boolean isStoppingOrStopped() {
        return stoppingOrStopped;
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