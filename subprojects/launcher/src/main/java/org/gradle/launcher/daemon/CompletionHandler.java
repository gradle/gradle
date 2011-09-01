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

package org.gradle.launcher.daemon;

import org.gradle.launcher.protocol.BusyException;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.util.UncheckedException;

import java.util.Date;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * synchronizes the daemon server work
*/
class CompletionHandler implements Stoppable {

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean running;
    private boolean stopped;

    private long lastActivityAt = -1;
    private ActivityListener activityListener = new EmptyActivityListener();

    public CompletionHandler setActivityListener(ActivityListener activityListener) {
        assert activityListener != null;
        this.activityListener = activityListener;
        return this;
    }

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
     * Has the daemon started accepting connections.
     */
    public boolean isStarted() {
        return lastActivityAt != -1;
    }

    public boolean hasBeenIdleFor(int milliseconds) {
        return lastActivityAt < (System.currentTimeMillis() - milliseconds);
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
            activityListener.onStart();
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
            activityListener.onComplete();
        } finally {
            lock.unlock();
        }
    }

    private void updateActivityTimestamp() {
        lastActivityAt = System.currentTimeMillis();
    }

    static interface ActivityListener {
        void onStart();
        void onComplete();
    }

    static class EmptyActivityListener implements ActivityListener {
        public void onStart() {}
        public void onComplete() {}
    }
}