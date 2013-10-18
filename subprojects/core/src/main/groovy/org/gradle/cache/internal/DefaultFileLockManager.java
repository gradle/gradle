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
package org.gradle.cache.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.filelock.*;
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.Factory;
import org.gradle.internal.Stoppable;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.NoZeroIntegerIdGenerator;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * Uses file system locks on a lock file per target file. Each lock file is made up of 2 regions:
 *
 * <ul>
 *     <li>State region: 1 byte version field, 4 bytes previous owner id (int value).</li>
 *     <li>Owner information region: 1 byte version field, bunch of other fields, see the code below for more info</li>
 * </ul>
 */
public class DefaultFileLockManager implements FileLockManager {
    private static final Logger LOGGER = Logging.getLogger(DefaultFileLockManager.class);
    public static final int DEFAULT_LOCK_TIMEOUT = 60000;

    private final Set<File> lockedFiles = new CopyOnWriteArraySet<File>();
    private final ProcessMetaDataProvider metaDataProvider;
    private final int lockTimeoutMs;
    private final IdGenerator<Long> generator;
    private final StateInfoProtocol stateInfoProtocol;
    private final FileLockContentionHandler fileLockContentionHandler;
    private final long shortTimeoutMs = 10000;
    private final int ownerId = new NoZeroIntegerIdGenerator().generateId();

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, FileLockContentionHandler fileLockContentionHandler) {
        this(metaDataProvider, DEFAULT_LOCK_TIMEOUT, fileLockContentionHandler, new DefaultStateInfoProtocol());
    }

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, int lockTimeoutMs, FileLockContentionHandler fileLockContentionHandler, StateInfoProtocol stateInfoProtocol) {
        this(metaDataProvider, lockTimeoutMs, fileLockContentionHandler, new RandomLongIdGenerator(), stateInfoProtocol);
    }

    DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, int lockTimeoutMs, FileLockContentionHandler fileLockContentionHandler,
                           IdGenerator<Long> generator, StateInfoProtocol stateInfoProtocol) {
        this.metaDataProvider = metaDataProvider;
        this.lockTimeoutMs = lockTimeoutMs;
        this.fileLockContentionHandler = fileLockContentionHandler;
        this.generator = generator;
        this.stateInfoProtocol = stateInfoProtocol;
    }

    public FileLock lock(File target, LockMode mode, String targetDisplayName) throws LockTimeoutException {
        return lock(target, mode, targetDisplayName, "");
    }

    public FileLock lock(File target, LockMode mode, String targetDisplayName, String operationDisplayName) {
        if (mode == LockMode.None) {
            throw new UnsupportedOperationException(String.format("No %s mode lock implementation available.", mode));
        }
        File canonicalTarget = GFileUtils.canonicalise(target);
        if (!lockedFiles.add(canonicalTarget)) {
            throw new IllegalStateException(String.format("Cannot lock %s as it has already been locked by this process.", targetDisplayName));
        }
        try {
            int port = fileLockContentionHandler.reservePort();
            return new DefaultFileLock(canonicalTarget, mode, targetDisplayName, operationDisplayName, port);
        } catch (Throwable t) {
            lockedFiles.remove(canonicalTarget);
            throw throwAsUncheckedException(t);
        }
    }

    public void allowContention(FileLock fileLock, Runnable whenContended) {
        fileLockContentionHandler.start(fileLock.getLockId(), whenContended);
    }

    private class DefaultFileLock extends AbstractFileAccess implements FileLock {
        private final File lockFile;
        private final File target;
        private final LockMode mode;
        private final String displayName;
        private final String operationDisplayName;
        private final boolean hasNewOwner;
        private java.nio.channels.FileLock lock;
        private FileLockAccess fileLockAccess;
        private boolean integrityViolated;
        private int port;
        private final long lockId;

        public DefaultFileLock(File target, LockMode mode, String displayName, String operationDisplayName, int port) throws Throwable {
            this.port = port;
            this.lockId = generator.generateId();
            if (mode == LockMode.None) {
                throw new UnsupportedOperationException("Locking mode None is not supported.");
            }

            this.target = target;

            this.displayName = displayName;
            this.operationDisplayName = operationDisplayName;
            if (target.isDirectory()) {
                lockFile = new File(target, target.getName() + ".lock");
            } else {
                lockFile = new File(target.getParentFile(), target.getName() + ".lock");
            }

            GFileUtils.mkdirs(lockFile.getParentFile());
            lockFile.createNewFile();
            fileLockAccess = new FileLockAccess(lockFile, displayName, new StateInfoAccess(stateInfoProtocol));
            try {
                lock = lock(mode);
                StateInfo stateInfo = fileLockAccess.readStateInfo();
                hasNewOwner = stateInfo.getPreviousOwnerId() != ownerId;
                integrityViolated = stateInfo.isDirty();
            } catch (Throwable t) {
                // Also releases any locks
                fileLockAccess.close();
                throw t;
            }

            this.mode = lock.isShared() ? LockMode.Shared : LockMode.Exclusive;
        }

        public boolean isLockFile(File file) {
            return file.equals(lockFile);
        }

        public boolean getUnlockedCleanly() {
            assertOpen();
            try {
                return !fileLockAccess.readStateInfo().isDirty();
            } catch (IOException e) {
                throw new RuntimeException("Unable to read state info from lock file: " + lockFile, e);
            }
        }

        public boolean getHasNewOwner() {
            return hasNewOwner;
        }

        public <T> T readFile(Factory<? extends T> action) throws LockTimeoutException, FileIntegrityViolationException {
            assertOpenAndIntegral();
            return action.create();
        }

        public void updateFile(Runnable action) throws LockTimeoutException, FileIntegrityViolationException {
            assertOpenAndIntegral();
            doWriteAction(action);
        }

        public void writeFile(Runnable action) throws LockTimeoutException {
            assertOpen();
            doWriteAction(action);
        }

        private void doWriteAction(Runnable action) {
            if (mode != LockMode.Exclusive) {
                throw new InsufficientLockModeException("An exclusive lock is required for this operation");
            }

            try {
                integrityViolated = true;
                fileLockAccess.markDirty();
                action.run();
                fileLockAccess.markClean(ownerId);
                integrityViolated = false;
            } catch (Throwable t) {
                throw throwAsUncheckedException(t);
            }
        }

        private void assertOpen() {
            if (lock == null) {
                throw new IllegalStateException("This lock has been closed.");
            }
        }

        private void assertOpenAndIntegral() {
            assertOpen();
            if (integrityViolated) {
                throw new FileIntegrityViolationException(String.format("The file '%s' was not unlocked cleanly", target));
            }
        }

        public void close() {
            CompositeStoppable stoppable = new CompositeStoppable();
            stoppable.add(new Stoppable() {
                public void stop() {
                    try {
                        fileLockContentionHandler.stop(lockId);
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to stop listening for file lock requests for " + displayName, e);
                    }
                }
            });
            stoppable.add(new Stoppable() {
                public void stop() {
                    if (fileLockAccess == null) {
                        return;
                    }
                    try {
                        LOGGER.debug("Releasing lock on {}.", displayName);
                        try {
                            if (lock != null && !lock.isShared()) {
                                // Discard information region
                                java.nio.channels.FileLock info;
                                try {
                                    info = lockInformationRegion(LockMode.Exclusive, System.currentTimeMillis() + shortTimeoutMs);
                                } catch (InterruptedException e) {
                                    throw throwAsUncheckedException(e);
                                }
                                if (info != null) {
                                    try {
                                        fileLockAccess.clearOwnerInfo();
                                    } finally {
                                        info.release();
                                    }
                                }
                            }
                        } finally {
                            fileLockAccess.close();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to release lock on " + displayName, e);
                    }
                }
            });
            stoppable.add(new Stoppable() {
                public void stop() {
                    lock = null;
                    fileLockAccess = null;
                    lockedFiles.remove(target);
                }
            });
            stoppable.stop();
        }

        public LockMode getMode() {
            return mode;
        }

        public long getLockId() {
            return lockId;
        }

        private java.nio.channels.FileLock lock(FileLockManager.LockMode lockMode) throws Throwable {
            LOGGER.debug("Waiting to acquire {} lock on {}.", lockMode.toString().toLowerCase(), displayName);
            long waitUntil = System.currentTimeMillis() + lockTimeoutMs;

            // Lock the state region, with the requested mode
            java.nio.channels.FileLock stateRegionLock = lockStateRegion(lockMode, waitUntil);
            if (stateRegionLock == null) {
                OwnerInfo ownerInfo = readInformationRegion(System.currentTimeMillis() + shortTimeoutMs);
                throw new LockTimeoutException(String.format("Timeout waiting to lock %s. It is currently in use by another Gradle instance.%nOwner PID: %s%nOur PID: %s%nOwner Operation: %s%nOur operation: %s%nLock file: %s",
                        displayName, ownerInfo.pid, metaDataProvider.getProcessIdentifier(), ownerInfo.operation, operationDisplayName, lockFile));
            }

            try {
                fileLockAccess.assertStateInfoIntegral();

                if (!stateRegionLock.isShared()) {
                    // We have an exclusive lock (whether we asked for it or not).
                    // Update the state region
                    fileLockAccess.ensureStateInfo();
                    // Acquire an exclusive lock on the information region and write our details there
                    java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Exclusive, System.currentTimeMillis() + shortTimeoutMs);
                    if (informationRegionLock == null) {
                        throw new IllegalStateException(String.format("Unable to lock the information region for %s", displayName));
                    }
                    // check that the length of the reserved region is enough for storing our content
                    try {
                        fileLockAccess.writeOwnerInfo(port, lockId, metaDataProvider.getProcessIdentifier(), operationDisplayName);
                    } finally {
                        informationRegionLock.release();
                    }
                }
            } catch (Throwable t) {
                stateRegionLock.release();
                throw t;
            }

            LOGGER.debug("Lock acquired.");
            return stateRegionLock;
        }

        private OwnerInfo readInformationRegion(long waitUntil) throws IOException, InterruptedException {
            // Can't acquire lock, get details of owner to include in the error message
            OwnerInfo out = new OwnerInfo();
            java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Shared, waitUntil);
            if (informationRegionLock == null) {
                LOGGER.debug("Could not lock information region for {}. Ignoring.", displayName);
            } else {
                try {
                    out = fileLockAccess.readOwnerInfo();
                } finally {
                    informationRegionLock.release();
                }
            }
            return out;
        }

        private java.nio.channels.FileLock lockStateRegion(LockMode lockMode, final long waitUntil) throws IOException, InterruptedException {
            do {
                java.nio.channels.FileLock fileLock = fileLockAccess.tryLockStateInfo(lockMode == LockMode.Shared);
                if (fileLock != null) {
                    return fileLock;
                }
                if (port != -1) { //we don't like the assumption about the port very much
                    OwnerInfo ownerInfo = readInformationRegion(System.currentTimeMillis()); //no need for timeout here, as we're already looping with timeout
                    if (ownerInfo.port != -1) {
                        LOGGER.debug("The file lock is held by a different Gradle process (pid: {}, operation: {}). Will attempt to ping owner at port {}", ownerInfo.pid, ownerInfo.operation, ownerInfo.port);
                        fileLockContentionHandler.pingOwner(ownerInfo.port, ownerInfo.lockId, displayName);
                    } else {
                        LOGGER.debug("The file lock is held by a different Gradle process. I was unable to read on which port the owner listens for lock access requests.");
                    }
                }
                //TODO SF it would really nice to print some message to the user after say 2 seconds of waiting
                //saying what gradle is doing, and why we're waiting.
                Thread.sleep(200L);
            } while (System.currentTimeMillis() < waitUntil);
            return null;
        }

        private java.nio.channels.FileLock lockInformationRegion(LockMode lockMode, long waitUntil) throws IOException, InterruptedException {
            do {
                java.nio.channels.FileLock fileLock = fileLockAccess.tryLockOwnerInfo(lockMode == LockMode.Shared);
                if (fileLock != null) {
                    return fileLock;
                }
                Thread.sleep(200L);
            }
            while (System.currentTimeMillis() < waitUntil);
            return null;
        }
    }
}