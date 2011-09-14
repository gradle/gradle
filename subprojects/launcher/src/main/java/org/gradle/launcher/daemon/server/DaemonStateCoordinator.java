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
import org.gradle.launcher.daemon.protocol.BusyException;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.util.UncheckedException;

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
class DaemonStateCoordinator implements Stoppable {

    private static final org.gradle.api.logging.Logger LOGGER = Logging.getLogger(DaemonStateCoordinator.class);

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean running;
    private boolean stopped;
    private long lastActivityAt = -1;

    /**
     * Waits until stopped.
     */
    public void awaitStop() {
        lock.lock();
        try {
            while (!stopped) {
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
            while ((running || !isStarted()) || (!stopped && !hasBeenIdleFor(timeout))) {
                try {
                    if (running || !isStarted()) {
                        condition.await();
                    } else {
                        condition.awaitUntil(new Date(lastActivityAt + timeout));
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.asUncheckedException(e);
                }
            }
            assert !running;
            return stopped;
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            LOGGER.info("Stop requested. The daemon is running a build: " + running);
            stopped = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void onStartActivity() {
        lock.lock();
        try {
            if (running) {
                throw new BusyException();
            }
            running = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void onActivityComplete() {
        lock.lock();
        try {
            running = false;
            updateActivityTimestamp();
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void updateActivityTimestamp() {
        lastActivityAt = System.currentTimeMillis();
    }

    /**
     * Has the daemon started accepting connections.
     */
    public boolean isStarted() {
        return lastActivityAt != -1;
    }

    public boolean hasBeenIdleFor(int milliseconds) {
        return lastActivityAt < (System.currentTimeMillis() - milliseconds);
    }

    public boolean isStopped() {
        return stopped;
    }

    public boolean isRunning() {
        return running;
    }

}