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
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.server.exec.DaemonStateControl;
import org.gradle.launcher.daemon.server.exec.DaemonStoppedException;
import org.gradle.launcher.daemon.server.exec.DaemonUnavailableException;
import org.slf4j.Logger;

import java.util.Date;
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
    private final long cancelTimeoutMs;

    private State state = State.Running;
    private long lastActivityAt = -1;
    private String currentCommandExecution;
    private Object result;
    private volatile DefaultBuildCancellationToken cancellationToken;

    private final StoppableExecutor executor;
    private final Runnable onStartCommand;
    private final Runnable onFinishCommand;

    public DaemonStateCoordinator(ExecutorFactory executorFactory, Runnable onStartCommand, Runnable onFinishCommand) {
        this(executorFactory, onStartCommand, onFinishCommand, 10 * 1000L);
    }

    DaemonStateCoordinator(ExecutorFactory executorFactory, Runnable onStartCommand, Runnable onFinishCommand, long cancelTimeoutMs) {
        executor = executorFactory.create("Daemon worker");
        this.onStartCommand = onStartCommand;
        this.onFinishCommand = onFinishCommand;
        this.cancelTimeoutMs = cancelTimeoutMs;
        updateActivityTimestamp();
        cancellationToken = new DefaultBuildCancellationToken();
    }

    private void setState(State state) {
        this.state = state;
        condition.signalAll();
    }

    private boolean awaitStop(long timeoutMs) {
        lock.lock();
        try {
            LOGGER.debug("Idle timeout: waiting for daemon to stop or be idle for {}ms", timeoutMs);
            while (true) {
                try {
                    switch (state) {
                        case Running:
                            if (isBusy()) {
                                LOGGER.debug(DaemonMessages.DAEMON_BUSY);
                                condition.await();
                            } else if (hasBeenIdleFor(timeoutMs)) {
                                LOGGER.debug("Idle timeout: daemon has been idle for requested period. Stopping now.");
                                stopNow("idle timeout");
                                return false;
                            } else {
                                Date waitUntil = new Date(lastActivityAt + timeoutMs);
                                LOGGER.debug(DaemonMessages.DAEMON_IDLE + waitUntil);
                                condition.awaitUntil(waitUntil);
                            }
                            break;
                        case Broken:
                            throw new IllegalStateException("This daemon is in a broken state.");
                        case StopRequested:
                            LOGGER.debug("Idle timeout: daemon stop has been requested. Sleeping until state changes.");
                            condition.await();
                            break;
                        case Stopped:
                            LOGGER.debug("Idle timeout: daemon has stopped.");
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

    public void stopOnIdleTimeout(int timeout, TimeUnit timeoutUnits) {
        awaitStop(timeoutUnits.toMillis(timeout));
    }

    public void requestStop() {
        lock.lock();
        try {
            LOGGER.debug("Stop as soon as idle requested. The daemon is busy: {}", isBusy());
            if (isBusy()) {
                beginStopping();
            } else {
                stopNow("stop requested and daemon idle");
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
     * @see #requestStop()
     */
    public void stop() {
        stopNow("service stop");
    }

    private void stopNow(String reason) {
        lock.lock();
        try {
            switch (state) {
                case Running:
                case Broken:
                case StopRequested:
                    LOGGER.debug("Marking daemon stopped due to {}. The daemon is running a build: {}", reason, isBusy());
                    setState(State.Stopped);
                    break;
                case Stopped:
                    break;
                default:
                    throw new IllegalStateException("Daemon is in unexpected state: " + state);
            }
        } finally {
            lock.unlock();
        }
    }

    private void beginStopping() {
        switch (state) {
            case Running:
            case Broken:
                setState(State.StopRequested);
                break;
            case StopRequested:
            case Stopped:
                break;
            default:
                throw new IllegalStateException("Daemon is in unexpected state: " + state);
        }
    }

    public void requestForcefulStop() {
        LOGGER.debug("Daemon stop requested.");
        stopNow("forceful stop requested");
    }

    public BuildCancellationToken getCancellationToken() {
        return cancellationToken;
    }

    private void updateCancellationToken() {
        cancellationToken = new DefaultBuildCancellationToken();
    }

    public void cancelBuild() {
        long waitUntil = System.currentTimeMillis() + cancelTimeoutMs;
        Date expiry = new Date(waitUntil);
        LOGGER.debug("Cancel requested: will wait for daemon to become idle.");
        try {
            cancellationToken.doCancel();
        } catch (Exception ex) {
            LOGGER.error("Cancel processing failed. Will continue.", ex);
        }

        lock.lock();
        try {
            while (System.currentTimeMillis() < waitUntil) {
                try {
                    switch (state) {
                        case Running:
                            if (isIdle()) {
                                LOGGER.debug("Cancel: daemon is idle now.");
                                return;
                            }
                            // fall-through
                        case StopRequested:
                            LOGGER.debug("Cancel: daemon is busy, sleeping until state changes.");
                            condition.awaitUntil(expiry);
                            break;
                        case Broken:
                            throw new IllegalStateException("This daemon is in a broken state.");
                        case Stopped:
                            LOGGER.debug("Cancel: daemon has stopped.");
                            return;
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            LOGGER.debug("Cancel: daemon is still busy after grace period. Will force stop.");
            stopNow("cancel requested");
        } finally {
            lock.unlock();
        }
    }

    public void runCommand(final Runnable command, String commandDisplayName) throws DaemonUnavailableException {
        onStartCommand(commandDisplayName);
        try {
            executor.execute(new Runnable() {
                public void run() {
                    try {
                        command.run();
                        onCommandSuccessful();
                    } catch (Throwable t) {
                        onCommandFailed(t);
                    }
                }
            });
            waitForCommandCompletion();
        } finally {
            onFinishCommand();
        }
    }

    private void waitForCommandCompletion() {
        lock.lock();
        try {
            while ((state == State.Running || state == State.StopRequested) && result == null) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            LOGGER.debug("Command execution: finished waiting for {}. Result {} with state {}", currentCommandExecution, result, state);
            if (result instanceof Throwable) {
                throw UncheckedException.throwAsUncheckedException((Throwable) result);
            }
            if (result != null) {
                return;
            }
            switch (state) {
                case Stopped:
                    throw new DaemonStoppedException();
                case Broken:
                    throw new DaemonUnavailableException("This daemon is broken and will stop.");
                default:
                    throw new IllegalStateException("Daemon is in unexpected state: " + state);
            }
        } finally {
            lock.unlock();
        }
    }

    private void onCommandFailed(Throwable failure) {
        lock.lock();
        try {
            result = failure;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void onCommandSuccessful() {
        lock.lock();
        try {
            result = this;
            condition.signalAll();
        } finally {
            lock.unlock();
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
            }

            LOGGER.debug("Command execution: started {} after {} minutes of idle", commandDisplayName, getIdleMinutes());
            try {
                onStartCommand.run();
                currentCommandExecution = commandDisplayName;
                result = null;
                updateActivityTimestamp();
                updateCancellationToken();
                condition.signalAll();
            } catch (Throwable throwable) {
                setState(State.Broken);
                throw UncheckedException.throwAsUncheckedException(throwable);
            }
        } finally {
            lock.unlock();
        }
    }

    private void onFinishCommand() {
        lock.lock();
        try {
            LOGGER.debug("Command execution: completed {}", currentCommandExecution);
            currentCommandExecution = null;
            result = null;
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
                    stopNow("command completed and stop requested");
                    break;
                case Stopped:
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

    boolean isWillRefuseNewCommands() {
        return state != State.Running;
    }

    boolean isIdle() {
        return state == State.Running && currentCommandExecution == null;
    }

    boolean isBusy() {
        return state == State.Running && currentCommandExecution != null;
    }
}