/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.cache.internal.locklistener;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.FileLockCommunicator;
import org.gradle.cache.internal.GracefullyStoppedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultFileLockContentionHandler implements FileLockContentionHandler, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(DefaultFileLockContentionHandler.class);
    private final Lock lock = new ReentrantLock();
    private final Map<Long, Runnable> contendedActions = new HashMap<Long, Runnable>();
    private final ExecutorFactory executorFactory;
    private final InetAddressFactory addressFactory;

    private FileLockCommunicator communicator;
    private StoppableExecutor executor;
    private boolean stopped;

    public DefaultFileLockContentionHandler(ExecutorFactory executorFactory, InetAddressFactory addressFactory) {
        this.executorFactory = executorFactory;
        this.addressFactory = addressFactory;
    }

    private Runnable listener() {
        return new Runnable() {
            public void run() {
                try {
                    LOGGER.debug("Starting file lock listener thread.");
                    doRun();
                } catch (Throwable t) {
                    //Logging exception here is only needed because by default Gradle does not show the stack trace
                    LOGGER.error("Problems handling incoming cache access requests.", t);
                } finally {
                    LOGGER.debug("File lock listener thread completed.");
                }
            }

            private void doRun() {
                while (true) {
                    long lockId;
                    try {
                        lockId = communicator.receive();
                    } catch (GracefullyStoppedException e) {
                        return;
                    }
                    lock.lock();
                    Runnable action;
                    try {
                        action = contendedActions.get(lockId);
                        if (action == null) {
                            //received access request for lock that is already closed
                            continue;
                        }
                    } finally {
                        lock.unlock();
                    }
                    action.run();
                }
            }
        };
    }

    public void start(long lockId, Runnable whenContended) {
        lock.lock();
        try {
            assertNotStopped();
            if (communicator == null) {
                throw new IllegalStateException("Must initialize the handler by reserving the port first.");
            }
            if (executor == null) {
                executor = executorFactory.create("File lock request listener");
                executor.execute(listener());
            }
            if (contendedActions.containsKey(lockId)) {
                throw new UnsupportedOperationException("Multiple contention actions for a given lock are currently not supported.");
            }
            contendedActions.put(lockId, whenContended);
        } finally {
            lock.unlock();
        }
    }

    public void pingOwner(int port, long lockId, String displayName) {
        getCommunicator().pingOwner(port, lockId, displayName);
    }

    private void assertNotStopped() {
        if (stopped) {
            throw new IllegalStateException(
                    "Cannot start managing file contention because this handler has been closed.");
        }
    }

    public void stop(long lockId) {
        lock.lock();
        try {
            contendedActions.remove(lockId);
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            stopped = true;
            contendedActions.clear();
            if (communicator != null) {
                communicator.stop();
            }
        } finally {
            lock.unlock();
        }
        if (executor != null) {
            executor.stop();
        }
    }

    public int reservePort() {
        return getCommunicator().getPort();
    }

    private FileLockCommunicator getCommunicator() {
        lock.lock();
        try {
            assertNotStopped();
            if (communicator == null) {
                communicator = new FileLockCommunicator(addressFactory);
            }
            return communicator;
        } finally {
            lock.unlock();
        }
    }
}
