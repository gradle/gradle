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

import org.gradle.cache.FileLockReleasedSignal;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static org.gradle.cache.internal.locklistener.FileLockPacketType.LOCK_RELEASE_CONFIRMATION;

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
 * The Lock Owner will inform this contention handler that it holds the lock via {@link #start(long, Consumer)}.
 * There it provides an action that this handler can call to release the lock, in case a release is requested.
 * <p>
 * A Lock Requester will notice that a lock is held by a Lock Holder by failing to lock the lock file.
 * It then turns to this contention via {@link #maybePingOwner(int, long, String, long, FileLockReleasedSignal)}.
 * <p>
 * Both Lock Holder and Lock Requester listen on a socket using {@link DefaultFileLockCommunicator}. The messages they
 * exchange contain only the lock id. If this contention handler receives such a message it determines if it
 * is a Lock Holder or a Lock Requester by checking if it knows an action to release the lock (i.e. if start() was
 * called for the lock in question).
 * <p>
 * If this is the Lock Owner:
 * <ul>
 *     <li>the contended action to release the lock is started, if it is not running already. The action might already run
 *         if several Lock Requester compete for the same lock or if confirmation took too long and the same Requester retries.</li>
 *     <li>the message is sent back to the Lock Requester to confirm that the lock release is in progress</li>
 *     <li>when the contended action finishes, i.e. the lock has been released, all Lock Requesters will get another message
 *         to trigger an immediate retry</li>
 * </ul>
 * <p>
 * If this is the Lock Requester:
 *    the message is interpreted as confirmation and stored. No further messages are sent to the Lock Owner via
 *    {@link #maybePingOwner(int, long, String, long, FileLockReleasedSignal)}.
 * <p>
 * As Lock Requester, the state of the request is always stored per lock (lockId) and Lock Holder (port). The Lock Holder
 * for a lock might change without acquiring the lock if several Lock Requester compete for the same lock.
 */
public class DefaultFileLockContentionHandler implements FileLockContentionHandler, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileLockContentionHandler.class);
    private static final int PING_DELAY = 1000;

    private final Lock lock = new ReentrantLock();

    private final Map<Long, ContendedAction> contendedActions = new HashMap<>();
    private final Map<Long, FileLockReleasedSignal> lockReleasedSignals = new HashMap<>();
    private final Map<Long, Integer> unlocksRequestedFrom = new HashMap<>();
    private final Map<Long, Integer> unlocksConfirmedFrom = new HashMap<>();

    private final FileLockCommunicator communicator;
    private final InetAddressProvider inetAddressProvider;
    private final ExecutorFactory executorFactory;

    private ManagedExecutor fileLockRequestListener;
    private ManagedExecutor unlockActionExecutor;

    private boolean stopped;
    private volatile boolean listenerFailed;

    public DefaultFileLockContentionHandler(ExecutorFactory executorFactory, InetAddressProvider inetAddressProvider) {
        this(new DefaultFileLockCommunicator(inetAddressProvider), inetAddressProvider, executorFactory);
    }

    DefaultFileLockContentionHandler(FileLockCommunicator communicator, InetAddressProvider inetAddressProvider, ExecutorFactory executorFactory) {
        this.communicator = communicator;
        this.inetAddressProvider = inetAddressProvider;
        this.executorFactory = executorFactory;
    }

    private Runnable listener() {
        return new Runnable() {
            private int failureCount = 0;

            @Override
            public void run() {
                try {
                    LOGGER.debug("Starting file lock listener thread.");
                    doRun();
                } finally {
                    LOGGER.debug("File lock listener thread completed.");
                }
            }

            private void doRun() {
                while (true) {
                    try {
                        // shutting down?
                        lock.lock();
                        try {
                            if (stopped) {
                                return;
                            }
                        } finally {
                            lock.unlock();
                        }

                        Optional<DatagramPacket> received = communicator.receive();

                        if (received.isPresent()) {
                            DatagramPacket packet = received.get();
                            FileLockPacketPayload payload = communicator.decode(packet);
                            lock.lock();
                            try {
                                ContendedAction contendedAction = contendedActions.get(payload.getLockId());
                                if (contendedAction == null) {
                                    acceptConfirmationAsLockRequester(payload, packet.getPort());
                                } else {
                                    contendedAction.addRequester(packet.getSocketAddress());
                                    if (!contendedAction.running) {
                                        startLockReleaseAsLockHolder(contendedAction);
                                    }
                                    communicator.confirmUnlockRequest(packet.getSocketAddress(), payload.getLockId());
                                }
                                // Processed a request so the socket is still working
                                failureCount = 0;
                            } finally {
                                lock.unlock();
                            }
                        }
                    } catch (Throwable t) {
                        failureCount++;
                        if (failureCount >= 100) {
                            // Something has gone very wrong and we're unable to communicate with other processes
                            LOGGER.error("Problems handling incoming lock requests.", t);
                            listenerFailed = true;
                            return;
                        }
                    }
                }
            }
        };
    }

    private void startLockReleaseAsLockHolder(ContendedAction contendedAction) {
        contendedAction.running = true;
        unlockActionExecutor.execute(contendedAction);
    }

    private void acceptConfirmationAsLockRequester(FileLockPacketPayload payload, Integer port) {
        long lockId = payload.getLockId();
        if (payload.getType() == LOCK_RELEASE_CONFIRMATION) {
            LOGGER.debug("Gradle process at port {} confirmed lock release for lock with id {}.", port, lockId);
            FileLockReleasedSignal signal = lockReleasedSignals.get(lockId);
            if (signal != null) {
                LOGGER.debug("Triggering lock release signal for lock with id {}.", lockId);
                signal.trigger();
            }
        } else {
            LOGGER.debug("Gradle process at port {} confirmed unlock request for lock with id {}.", port, lockId);
            unlocksConfirmedFrom.put(lockId, port);
        }
    }

    @Override
    public void start(long lockId, Consumer<FileLockReleasedSignal> whenContended) {
        lock.lock();
        try {
            // First time use, start up the executors that deal with lock contention
            if (unlockActionExecutor == null) {
                unlockActionExecutor = executorFactory.create("File lock release action executor");
            }
            if (fileLockRequestListener == null) {
                fileLockRequestListener = executorFactory.create("File lock request listener");
                fileLockRequestListener.execute(listener());
            }
            lockReleasedSignals.remove(lockId);
            unlocksRequestedFrom.remove(lockId);
            unlocksConfirmedFrom.remove(lockId);
            assertNotStopped();

            if (contendedActions.containsKey(lockId)) {
                throw new UnsupportedOperationException("Multiple contention actions for a given lock are currently not supported.");
            }
            contendedActions.put(lockId, new ContendedAction(lockId, whenContended));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean maybePingOwner(int port, long lockId, String displayName, long timeElapsed, FileLockReleasedSignal signal) {
        if (Integer.valueOf(port).equals(unlocksConfirmedFrom.get(lockId))) {
            //the unlock was confirmed we are waiting
            return false;
        }
        if (Integer.valueOf(port).equals(unlocksRequestedFrom.get(lockId)) && timeElapsed < PING_DELAY) {
            //the unlock was just requested but not yet confirmed, give it some more time
            return false;
        }

        boolean pingSentSuccessfully = getCommunicator().pingOwner(inetAddressProvider.getCommunicationAddress(), port, lockId, displayName);
        if (pingSentSuccessfully) {
            lock.lock();
            try {
                unlocksRequestedFrom.put(lockId, port);
                lockReleasedSignals.put(lockId, signal);
            } finally {
                lock.unlock();
            }
        }
        return pingSentSuccessfully;
    }

    @Override
    public boolean isRunning() {
        return !listenerFailed;
    }

    private void assertNotStopped() {
        if (stopped) {
            throw new IllegalStateException(
                    "Cannot start managing file contention because this handler has been closed.");
        }
    }

    @Override
    public void stop(long lockId) {
        lock.lock();
        try {
            contendedActions.remove(lockId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            stopped = true;
            contendedActions.clear();
            communicator.stop();
        } finally {
            lock.unlock();
        }

        if (unlockActionExecutor != null) {
            unlockActionExecutor.stop();
        }
        if (fileLockRequestListener != null) {
            fileLockRequestListener.stop();
        }
    }

    @Override
    public int reservePort() {
        lock.lock();
        try {
            assertNotStopped();
        } finally {
            lock.unlock();
        }
        return getCommunicator().getPort();
    }

    private FileLockCommunicator getCommunicator() {
        return communicator;
    }

    private class ContendedAction implements Runnable {
        private final Lock lock = new ReentrantLock();
        private final long lockId;
        private final Consumer<FileLockReleasedSignal> action;
        private Set<SocketAddress> requesters = new LinkedHashSet<>();
        private boolean running;

        private ContendedAction(long lockId, Consumer<FileLockReleasedSignal> action) {
            this.lockId = lockId;
            this.action = action;
        }

        @Override
        public void run() {
            action.accept(() -> {
                Set<SocketAddress> requesters = consumeRequesters();
                if (requesters == null) {
                    throw new IllegalStateException("trigger() has already been called and must at most be called once");
                }
                communicator.confirmLockRelease(requesters, lockId);
            });
        }

        private void addRequester(SocketAddress contender) {
            lock.lock();
            try {
                if (requesters != null) {
                    requesters.add(contender);
                }
            } finally {
                lock.unlock();
            }
        }

        private Set<SocketAddress> consumeRequesters() {
            lock.lock();
            try {
                return requesters;
            } finally {
                requesters = null;
                lock.unlock();
            }
        }
    }

}
