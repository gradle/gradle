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

package org.gradle.launcher;

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
    private long expiry;
    private final int idleDaemonTimeout;
    private final PersistentDaemonRegistry.Registry registryFile;

    CompletionHandler(int idleDaemonTimeout, PersistentDaemonRegistry.Registry registryFile) {
        this.idleDaemonTimeout = idleDaemonTimeout;
        this.registryFile = registryFile;
        resetTimer();
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Waits until stopped, or timeout.
     *
     * @return true if stopped, false if timeout
     */
    public boolean awaitStop() {
        lock.lock();
        try {
            while (running || (!stopped && System.currentTimeMillis() < expiry)) {
                try {
                    if (running) {
                        condition.await();
                    } else {
                        condition.awaitUntil(new Date(expiry));
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
            registryFile.markBusy();
        } finally {
            lock.unlock();
        }
    }

    public void onActivityComplete() {
        lock.lock();
        try {
            running = false;
            resetTimer();
            condition.signalAll();
            registryFile.markIdle();
        } finally {
            lock.unlock();
        }
    }

    private void resetTimer() {
        expiry = System.currentTimeMillis() + idleDaemonTimeout;
    }

}
