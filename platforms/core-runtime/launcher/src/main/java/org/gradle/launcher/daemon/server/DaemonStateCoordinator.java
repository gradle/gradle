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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.launcher.daemon.server.api.DaemonState;
import org.gradle.launcher.daemon.server.api.DaemonStateControl;
import org.gradle.launcher.daemon.server.api.DaemonStoppedException;
import org.gradle.launcher.daemon.server.api.DaemonUnavailableException;

import javax.annotation.Nullable;
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
    public static final String DAEMON_WILL_STOP_MESSAGE = "Daemon will be stopped at the end of the build ";
    public static final String DAEMON_STOPPING_IMMEDIATELY_MESSAGE = "Daemon is stopping immediately ";
    private static final Logger LOGGER = Logging.getLogger(DaemonStateCoordinator.class);

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final long cancelTimeoutMs;

    private DaemonState state = DaemonState.Idle;
    private final Timer idleTimer;
    private String currentCommandExecution;
    private Object result;
    private String stopReason;
    private volatile DefaultBuildCancellationToken cancellationToken;

    private final ManagedExecutor executor;
    private final Runnable onStartCommand;
    private final Runnable onFinishCommand;
    private final Runnable onCancelCommand;

    public DaemonStateCoordinator(ExecutorFactory executorFactory, Runnable onStartCommand, Runnable onFinishCommand, Runnable onCancelCommand) {
        this(executorFactory, onStartCommand, onFinishCommand, onCancelCommand, 10 * 1000L);
    }

    DaemonStateCoordinator(ExecutorFactory executorFactory, Runnable onStartCommand, Runnable onFinishCommand, Runnable onCancelCommand, long cancelTimeoutMs) {
        executor = executorFactory.create("Daemon worker");
        this.onStartCommand = onStartCommand;
        this.onFinishCommand = onFinishCommand;
        this.onCancelCommand = onCancelCommand;
        this.cancelTimeoutMs = cancelTimeoutMs;
        idleTimer = Time.startTimer();
        updateCancellationToken();
    }

    private void setState(DaemonState state) {
        this.state = state;
        condition.signalAll();
    }

    DaemonStopState awaitStop() {
        lock.lock();
        try {
            while (true) {
                try {
                    switch (state) {
                        case Idle:
                        case Busy:
                            LOGGER.debug("daemon is running. Sleeping until state changes.");
                            condition.await();
                            break;
                        case Canceled:
                            LOGGER.debug("cancel requested.");
                            DaemonStopState state = cancelNow();
                            if (state != null) {
                                // Could not cancel cleanly, so stop
                                return state;
                            }
                            // Else, cancelled cleanly, so keep doing work
                            break;
                        case Broken:
                            throw new IllegalStateException("This daemon is in a broken state.");
                        case StopRequested:
                            LOGGER.debug("daemon stop has been requested. Sleeping until state changes.");
                            condition.await();
                            break;
                        case Stopped:
                            LOGGER.debug("daemon has stopped.");
                            return DaemonStopState.Clean;
                        case ForceStopped:
                            LOGGER.debug("daemon has been force stopped.");
                            return DaemonStopState.Forced;
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void requestStop(String reason) {
        lock.lock();
        try {
            if (state != DaemonState.StopRequested && state != DaemonState.Stopped && state != DaemonState.ForceStopped) {
                LOGGER.lifecycle(DAEMON_WILL_STOP_MESSAGE + reason);
                if (state == DaemonState.Busy) {
                    LOGGER.debug("Stop as soon as idle requested. The daemon is busy");
                    beginStopping();
                } else {
                    stopNow(reason);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void requestForcefulStop(String reason) {
        LOGGER.lifecycle(DAEMON_STOPPING_IMMEDIATELY_MESSAGE + reason);
        stopNow(reason);
    }

    /**
     * Forcibly stops the daemon, even if it is busy.
     *
     * If the daemon is busy and the client is waiting for a response, it may receive "null" from the daemon as the connection may be closed by this method before the result is sent back.
     *
     * @see #requestStop(String reason)
     */
    @Override
    public void stop() {
        stopNow("service stop");
    }

    private void stopNow(String reason) {
        lock.lock();
        try {
            switch (state) {
                case Idle:
                    LOGGER.debug("Marking daemon stopped due to {}. The daemon is not running a build", reason);
                    stopReason = reason;
                    setState(DaemonState.Stopped);
                    break;
                case Busy:
                case Canceled:
                case Broken:
                case StopRequested:
                    LOGGER.debug("Marking daemon stopped due to {}. The daemon is running a build", reason);
                    stopReason = reason;
                    setState(DaemonState.ForceStopped);
                    break;
                case Stopped:
                case ForceStopped:
                    // Already stopped
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
            case Idle:
            case Busy:
            case Canceled:
            case Broken:
                setState(DaemonState.StopRequested);
                break;
            case StopRequested:
            case Stopped:
            case ForceStopped:
                break;
            default:
                throw new IllegalStateException("Daemon is in unexpected state: " + state);
        }
    }

    @Override
    public BuildCancellationToken getCancellationToken() {
        return cancellationToken;
    }

    private void updateCancellationToken() {
        cancellationToken = new DefaultBuildCancellationToken();
        cancellationToken.addCallback(onCancelCommand);
    }

    @Override
    public void requestCancel() {
        lock.lock();
        try {
            if (state == DaemonState.Busy) {
                setState(DaemonState.Canceled);
            } else if (state == DaemonState.StopRequested) {
                requestForcefulStop("the build was canceled after a stop was requested");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void cancelBuild() {
        requestCancel();

        lock.lock();
        try {
            while (true) {
                try {
                    switch (state) {
                        case Idle:
                        case Stopped:
                        case ForceStopped:
                            return;
                        case Busy:
                        case Canceled:
                        case StopRequested:
                            condition.await();
                            break;
                        case Broken:
                            throw new IllegalStateException("This daemon is in a broken state.");
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * @return null if the current build could be cancelled cleanly, otherwise the result of stopping the daemon
     */
    @Nullable
    private DaemonStopState cancelNow() {
        CountdownTimer timer = Time.startCountdownTimer(cancelTimeoutMs);

        LOGGER.debug("Cancel requested: will wait for daemon to become idle.");
        try {
            cancellationToken.cancel();
        } catch (Exception ex) {
            LOGGER.error("Cancel processing failed. Will continue.", ex);
        }

        lock.lock();
        try {
            while (!timer.hasExpired()) {
                try {
                    switch (state) {
                        case Idle:
                            LOGGER.debug("Cancel: daemon is idle now.");
                            return null;
                        case Busy:
                        case Canceled:
                        case StopRequested:
                            LOGGER.debug("Cancel: daemon is busy, sleeping until state changes.");
                            condition.await(timer.getRemainingMillis(), TimeUnit.MILLISECONDS);
                            break;
                        case Broken:
                            throw new IllegalStateException("This daemon is in a broken state.");
                        case Stopped:
                            LOGGER.debug("Cancel: daemon has stopped.");
                            return DaemonStopState.Clean;
                        case ForceStopped:
                            LOGGER.debug("Cancel: daemon has been force stopped.");
                            return DaemonStopState.Forced;
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            LOGGER.debug("Cancel: daemon is still busy after grace period. Will force stop.");
            stopNow("cancel requested but timed out");
            return DaemonStopState.Forced;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void runCommand(final Runnable command, String commandDisplayName) throws DaemonUnavailableException {
        onStartCommand(commandDisplayName);
        try {
            executor.execute(() -> {
                try {
                    command.run();
                    onCommandSuccessful();
                } catch (Throwable t) {
                    onCommandFailed(t);
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
            while ((state == DaemonState.Busy || state == DaemonState.Canceled || state == DaemonState.StopRequested) && result == null) {
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
                case ForceStopped:
                    throw new DaemonStoppedException(stopReason);
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
                case ForceStopped:
                    throw new DaemonUnavailableException("This daemon has stopped.");
                case Busy:
                case Canceled:
                    throw new DaemonUnavailableException(String.format("This daemon is currently executing: %s", currentCommandExecution));
                case Idle:
                    break;
            }

            LOGGER.debug("Command execution: started {} after {} minutes of idle", commandDisplayName, getIdleMinutes());
            try {
                setState(DaemonState.Busy);
                onStartCommand.run();
                currentCommandExecution = commandDisplayName;
                result = null;
                updateActivityTimestamp();
                updateCancellationToken();
                condition.signalAll();
            } catch (Throwable throwable) {
                setState(DaemonState.Broken);
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
            stopReason = null;
            updateActivityTimestamp();
            switch (state) {
                case Idle:
                case Busy:
                case Canceled:
                    try {
                        onFinishCommand.run();
                        setState(DaemonState.Idle);
                    } catch (Throwable throwable) {
                        setState(DaemonState.Broken);
                        throw UncheckedException.throwAsUncheckedException(throwable);
                    }
                    break;
                case StopRequested:
                    setState(DaemonState.Idle);
                    stopNow("command completed and stop requested");
                    break;
                case Stopped:
                case ForceStopped:
                    break;
                default:
                    throw new IllegalStateException("Daemon is in unexpected state: " + state);
            }
        } finally {
            lock.unlock();
        }
    }

    private void updateActivityTimestamp() {
        LOGGER.debug("resetting idle timer");
        idleTimer.reset();
    }

    private double getIdleMinutes() {
        lock.lock();
        try {
            return idleTimer.getElapsedMillis() / 1000.0 / 60.0;
        } finally {
            lock.unlock();
        }
    }

    public long getIdleMillis() {
        if (state == DaemonState.Idle) {
            return idleTimer.getElapsedMillis();
        } else {
            return 0L;
        }
    }

    boolean isWillRefuseNewCommands() {
        return !(state == DaemonState.Idle || state == DaemonState.Busy);
    }

    @Override
    public DaemonState getState() {
        return state;
    }
}
