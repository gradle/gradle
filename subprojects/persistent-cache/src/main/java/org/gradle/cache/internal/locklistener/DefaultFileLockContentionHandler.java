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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The contention handler is responsible for negotiating the transfer of a lock from one process to another.
 * Several processes might request the same lock at the same time. In such a situation, there is:
 * <ul>
 *     <li>One Lock Holder</li>
 *     <li>One or more Lock Requester</li>
 * </ul>
 * The general strategy is that the Lock Holder keeps locks open as long as there is no Lock Requester. This is,
 * because each lock open/close action requires File I/O which is expensive.
 * <p>
 * The Lock Owner will inform this contention handler that it holds the lock via {@link #start(long, Runnable)}.
 * There it provides an action that this handler can call to release the lock, in case a release is requested.
 * <p>
 * A Lock Requester will notice that a lock is held by a Lock Holder by failing to lock the lock file.
 * It then turns to this contention via {@link #maybePingOwner(int, long, String, long)}.
 * <p>
 * Both Lock Holder and Lock Requester listen on a socket using {@link FileLockCommunicator}. The messages they
 * exchange contain only the lock id. If this contention handler receives such a message it determines if it
 * is a Lock Holder or a Lock Requester by checking if it knows an action to release the lock (i.e. if start() was
 * called for the lock in question).
 * <p>
 * If this is the Lock Owner:
 * <ul>
 *     <li>the contended action to release the lock is started, if it is not running already. The action might already run
 *         if several Lock Requester compete for the same lock or if confirmation took too long and the same Requester retries.</li>
 *     <li>the message is sent back to the Lock Requester to confirm that the lock release is in progress</li>
 * </ul>
 * <p>
 * If this is the Lock Requester:
 *     <li>the message is interpreted as confirmation and stored. No further messages are sent to the Lock Owner via
 *    {@link #maybePingOwner(int, long, String, long)}.</li>
 * <p>
 * As Lock Requester, the state of the request is always stored per lock (lockId) and Lock Holder (port). The Lock Holder
 * for a lock might change without acquiring the lock if several Lock Requester compete for the same lock.
 */
public class DefaultFileLockContentionHandler implements FileLockContentionHandler, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileLockContentionHandler.class);
    private static final int PING_DELAY = 1000;
    private final Lock lock = new ReentrantLock();

    private final Map<Long, ContendedAction> contendedActions = new HashMap<Long, ContendedAction>();
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

                    lock.lock();
                    ContendedAction contendedAction = contendedActions.get(lockId);
                    if (contendedAction == null) {
                        acceptConfirmationAsLockRequester(lockId, packet.getPort());
                    } else {
                        if (!contendedAction.running) {
                            startLockReleaseAsLockHolder(contendedAction);
                        }
                        communicator.confirmUnlockRequest(packet);
                    }
                    lock.unlock();
                }
            }
        };
    }

    private void startLockReleaseAsLockHolder(ContendedAction contendedAction) {
        contendedAction.running = true;
        unlockActionExecutor.execute(contendedAction.action);
    }

    private void acceptConfirmationAsLockRequester(long lockId, int port) {
        unlocksConfirmedFrom.put(lockId, port);
        LOGGER.debug("Gradle process at port {} confirmed unlock request for lock with id {}.", port, lockId);
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
            contendedActions.put(lockId, new ContendedAction(whenContended));
        } finally {
            lock.unlock();
        }
    }

    public boolean maybePingOwner(int port, long lockId, String displayName, long timeElapsed) {
        if (Integer.valueOf(port).equals(unlocksConfirmedFrom.get(lockId))) {
            //the unlock was confirmed we are waiting
            return false;
        }
        if (Integer.valueOf(port).equals(unlocksRequestedFrom.get(lockId)) && timeElapsed < PING_DELAY) {
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

    private static class ContendedAction {
        private final Runnable action;
        private boolean running;

        private ContendedAction(Runnable action) {
            this.action = action;
        }
    }
}
