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

import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultFileLockContentionHandler implements FileLockContentionHandler, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileLockContentionHandler.class);
    private static final int PING_DELAY = 1000;
    private final Lock lock = new ReentrantLock();
    private final Map<Long, Runnable> contendedActions = new HashMap<Long, Runnable>();
    private final List<Long> contendedActionsRunning = new ArrayList<Long>();
    private final Map<Long, Integer> unlocksRequestedFrom = new HashMap<Long, Integer>();
    private final Map<Long, Integer> unlocksConfirmedFrom = new HashMap<Long, Integer>();
    private final ExecutorFactory executorFactory;
    private final InetAddressFactory addressFactory;

    private FileLockCommunicator communicator;
    private ManagedExecutor fileLockRequestListener;
    private ManagedExecutor unlockActionExecutor;
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
                    DatagramPacket packet;
                    long lockId;
                    try {
                        packet = communicator.receive();
                        lockId = communicator.decodeLockId(packet);
                    } catch (GracefullyStoppedException e) {
                        return;
                    }
                    Runnable action;

                    lock.lock();
                    if (contendedActionsRunning.contains(lockId)) {
                        action = null;
                    } else {
                        action = contendedActions.get(lockId);
                        if (action == null) {
                            // The other side has the action and confirmed now that it started it
                            unlocksConfirmedFrom.put(lockId, packet.getPort());
                            LOGGER.debug("Gradle process at port {} confirmed unlock request for lock with id {}.", packet.getPort(), lockId);
                        } else {
                            contendedActionsRunning.add(lockId);
                        }

                    }
                    lock.unlock();

                    if (action != null) {
                        // I have the action, I execute it and tell the other side that I am on it
                        unlockActionExecutor.execute(action);
                        communicator.confirmUnlockRequest(packet);
                    }
                }
            }
        };
    }

    public void start(long lockId, Runnable whenContended) {
        lock.lock();
        unlocksRequestedFrom.remove(lockId);
        unlocksConfirmedFrom.remove(lockId);
        try {
            assertNotStopped();
            if (communicator == null) {
                throw new IllegalStateException("Must initialize the handler by reserving the port first.");
            }
            if (fileLockRequestListener == null) {
                fileLockRequestListener = executorFactory.create("File lock request listener");
                fileLockRequestListener.execute(listener());
            }
            if (unlockActionExecutor == null) {
                unlockActionExecutor = executorFactory.create("File lock release action executor");
            }
            if (contendedActions.containsKey(lockId)) {
                throw new UnsupportedOperationException("Multiple contention actions for a given lock are currently not supported.");
            }
            contendedActions.put(lockId, whenContended);
        } finally {
            lock.unlock();
        }
    }

    public boolean maybePingOwner(int port, long lockId, String displayName, long timeElapsed) {
        if (unlocksConfirmedFrom.containsKey(lockId) && unlocksConfirmedFrom.get(lockId) == port) {
            //the unlock was confirmed we are waiting
            return false;
        }
        if (unlocksRequestedFrom.containsKey(lockId) && unlocksRequestedFrom.get(lockId) == port && timeElapsed < PING_DELAY) {
            //the unlock was just requested but not yet confirmed, give it some more time
            return false;
        }

        boolean pingSentSuccessfully = getCommunicator().pingOwner(port, lockId, displayName);
        if (pingSentSuccessfully) {
            lock.lock();
            unlocksRequestedFrom.put(lockId, port);
            lock.unlock();
        }
        return pingSentSuccessfully;
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
            contendedActionsRunning.remove(lockId);
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
        if (fileLockRequestListener != null) {
            fileLockRequestListener.stop();
        }
        if (unlockActionExecutor != null) {
            unlockActionExecutor.stop();
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
