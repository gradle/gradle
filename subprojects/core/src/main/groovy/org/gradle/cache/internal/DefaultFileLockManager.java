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
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.Factory;
import org.gradle.internal.Stoppable;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.util.GFileUtils;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * Uses file system locks on a lock file per target file. Each lock file is made up of 2 regions:
 *
 * <ul>
 *     <li>State region: 1 byte version field, 1 byte clean flag.</li>
 *     <li>Owner information region: 1 byte version field, bunch of other fields, see the code below for more info</li>
 * </ul>
 */
public class DefaultFileLockManager implements FileLockManager {
    private static final Logger LOGGER = Logging.getLogger(DefaultFileLockManager.class);
    private static final int DEFAULT_LOCK_TIMEOUT = 60000;
    private static final byte STATE_REGION_PROTOCOL = 1;
    private static final int STATE_REGION_SIZE = 2;
    private static final int STATE_REGION_POS = 0;
    private static final byte INFORMATION_REGION_PROTOCOL = 3;
    private static final int INFORMATION_REGION_POS = STATE_REGION_POS + STATE_REGION_SIZE;
    public static final int INFORMATION_REGION_SIZE = 2052;
    public static final int INFORMATION_REGION_DESCR_CHUNK_LIMIT = 340;
    private final Set<File> lockedFiles = new CopyOnWriteArraySet<File>();
    private final ProcessMetaDataProvider metaDataProvider;
    private final int lockTimeoutMs;
    private final IdGenerator<Long> generator;
    private FileLockContentionHandler fileLockContentionHandler;
    private final long shortTimeoutMs = 10000;

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, FileLockContentionHandler fileLockContentionHandler) {
        this(metaDataProvider, DEFAULT_LOCK_TIMEOUT, fileLockContentionHandler);
    }

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, int lockTimeoutMs, FileLockContentionHandler fileLockContentionHandler) {
        this(metaDataProvider, lockTimeoutMs, fileLockContentionHandler, new RandomLongIdGenerator());
    }

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, int lockTimeoutMs, FileLockContentionHandler fileLockContentionHandler, IdGenerator<Long> generator) {
        this.metaDataProvider = metaDataProvider;
        this.lockTimeoutMs = lockTimeoutMs;
        this.fileLockContentionHandler = fileLockContentionHandler;
        this.generator = generator;
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
            DefaultFileLock newLock = new DefaultFileLock(canonicalTarget, mode, targetDisplayName, operationDisplayName, port);
            return newLock;
        } catch (Throwable t) {
            lockedFiles.remove(canonicalTarget);
            throw throwAsUncheckedException(t);
        }
    }

    public void allowContention(FileLock fileLock, Runnable whenContended) {
        fileLockContentionHandler.start(fileLock.getLockId(), whenContended);
    }

    private class OwnerInfo {
        int port;
        long lockId;
        String pid;
        String operation;
    }

    private class DefaultFileLock extends AbstractFileAccess implements FileLock {
        private final File lockFile;
        private final File target;
        private final LockMode mode;
        private final String displayName;
        private final String operationDisplayName;
        private java.nio.channels.FileLock lock;
        private RandomAccessFile lockFileAccess;
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
            lockFileAccess = new RandomAccessFile(lockFile, "rw");
            try {
                lock = lock(mode);
                integrityViolated = !getUnlockedCleanly();
            } catch (Throwable t) {
                // Also releases any locks
                lockFileAccess.close();
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
                lockFileAccess.seek(STATE_REGION_POS + 1);
                if (!lockFileAccess.readBoolean()) {
                    // Process has crashed while updating target file
                    return false;
                }
            } catch (EOFException e) {
                // Process has crashed writing to lock file
                return false;
            } catch (Exception e) {
                throw throwAsUncheckedException(e);
            }

            return true;
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
                markDirty();
                action.run();
                markClean();
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

        private void markClean() throws IOException {
            lockFileAccess.seek(STATE_REGION_POS);
            lockFileAccess.writeByte(STATE_REGION_PROTOCOL);
            lockFileAccess.writeBoolean(true);
            assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
        }

        private void markDirty() throws IOException {
            lockFileAccess.seek(STATE_REGION_POS);
            lockFileAccess.writeByte(STATE_REGION_PROTOCOL);
            lockFileAccess.writeBoolean(false);
            assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
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
                    if (lockFileAccess == null) {
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
                                        lockFileAccess.setLength(INFORMATION_REGION_POS);
                                    } finally {
                                        info.release();
                                    }
                                }
                            }
                        } finally {
                            lockFileAccess.close();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Problems releasing lock on " + displayName, e);
                    }
                }
            });
            stoppable.add(new Stoppable() {
                public void stop() {
                    lock = null;
                    lockFileAccess = null;
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
                if (lockFileAccess.length() > 0) {
                    lockFileAccess.seek(STATE_REGION_POS);
                    if (lockFileAccess.readByte() != STATE_REGION_PROTOCOL) {
                        throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file '%s' for %s.", lockFile, displayName));
                    }
                }

                if (!stateRegionLock.isShared()) {
                    // We have an exclusive lock (whether we asked for it or not).
                    // Update the state region
                    if (lockFileAccess.length() < STATE_REGION_SIZE) {
                        // File did not exist before locking
                        lockFileAccess.seek(STATE_REGION_POS);
                        lockFileAccess.writeByte(STATE_REGION_PROTOCOL);
                        lockFileAccess.writeBoolean(false);
                    }
                    // Acquire an exclusive lock on the information region and write our details there
                    java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Exclusive, System.currentTimeMillis() + shortTimeoutMs);
                    if (informationRegionLock == null) {
                        throw new IllegalStateException(String.format("Unable to lock the information region for lock %s", displayName));
                    }
                    // check that the length of the reserved region is enough for storing our content
                    try {
                        lockFileAccess.seek(INFORMATION_REGION_POS);
                        lockFileAccess.writeByte(INFORMATION_REGION_PROTOCOL);
                        lockFileAccess.writeInt(port);
                        lockFileAccess.writeLong(lockId);
                        lockFileAccess.writeUTF(trimIfNecessary(metaDataProvider.getProcessIdentifier()));
                        lockFileAccess.writeUTF(trimIfNecessary(operationDisplayName));
                        lockFileAccess.setLength(lockFileAccess.getFilePointer());
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
            out.pid = "unknown";
            out.operation = "unknown";
            out.port = -1;
            java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Shared, waitUntil);
            if (informationRegionLock == null) {
                LOGGER.debug("Could not lock information region for {}. Ignoring.", displayName);
            } else {
                try {
                    if (lockFileAccess.length() <= INFORMATION_REGION_POS) {
                        LOGGER.debug("Lock file for {} is too short to contain information region. Ignoring.", displayName);
                    } else {
                        lockFileAccess.seek(INFORMATION_REGION_POS);
                        if (lockFileAccess.readByte() != INFORMATION_REGION_PROTOCOL) {
                            throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file '%s' for %s.", lockFile, displayName));
                        }
                        out.port = lockFileAccess.readInt();
                        out.lockId = lockFileAccess.readLong();
                        out.pid = lockFileAccess.readUTF();
                        out.operation = lockFileAccess.readUTF();
                        LOGGER.debug("Read following information from the file lock info region. Port: {}, owner: {}, operation: {}", out.port, out.pid, out.operation);
                    }
                } finally {
                    informationRegionLock.release();
                }
            }
            return out;
        }

        private String trimIfNecessary(String inputString) {
            if(inputString.length() > INFORMATION_REGION_DESCR_CHUNK_LIMIT){
                return inputString.substring(0, INFORMATION_REGION_DESCR_CHUNK_LIMIT);
            } else {
                return inputString;
            }
        }

        private java.nio.channels.FileLock lockStateRegion(LockMode lockMode, final long waitUntil) throws IOException, InterruptedException {
            do {
                java.nio.channels.FileLock fileLock = lockRegion(lockMode, STATE_REGION_POS, STATE_REGION_SIZE);
                if (fileLock != null) {
                    return fileLock;
                }
                if (port != -1) { //we don't like the assumption about the port very much
                    OwnerInfo ownerInfo = readInformationRegion(System.currentTimeMillis()); //no need for timeout here, as we're already looping with timeout
                    if (ownerInfo.port != -1) {
                        LOGGER.info("The file lock is held by a different Gradle process (pid: {}, operation: {}). Will attempt to ping owner at port {}", ownerInfo.pid, ownerInfo.operation, ownerInfo.port);
                        FileLockCommunicator.pingOwner(ownerInfo.port, ownerInfo.lockId);
                    } else {
                        LOGGER.debug("The file lock is held by a different Gradle process. I was unable to read on which port the owner listens for lock access requests.");
                    }
                }
                Thread.sleep(200L);
            } while (System.currentTimeMillis() < waitUntil);
            return null;
        }

        private java.nio.channels.FileLock lockInformationRegion(LockMode lockMode, long waitUntil) throws IOException, InterruptedException {
            do {
                java.nio.channels.FileLock fileLock = lockRegion(lockMode, INFORMATION_REGION_POS, INFORMATION_REGION_SIZE - INFORMATION_REGION_POS);
                if (fileLock != null) {
                    return fileLock;
                }
                Thread.sleep(200L);
            }
            while (System.currentTimeMillis() < waitUntil);
            return null;
        }

        private java.nio.channels.FileLock lockRegion(LockMode lockMode, long start, long size) throws IOException, InterruptedException {
            return lockFileAccess.getChannel().tryLock(start, size, lockMode == LockMode.Shared);
        }
    }
}