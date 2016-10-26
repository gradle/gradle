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
import org.gradle.internal.FileUtils;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.time.Timers;
import org.gradle.internal.time.TrueTimeProvider;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * Uses file system locks on a lock file per target file.
 */
public class DefaultFileLockManager implements FileLockManager {
    private static final Logger LOGGER = Logging.getLogger(DefaultFileLockManager.class);
    public static final int DEFAULT_LOCK_TIMEOUT = 60000;

    private final Set<File> lockedFiles = new CopyOnWriteArraySet<File>();
    private final ProcessMetaDataProvider metaDataProvider;
    private final int lockTimeoutMs;
    private final IdGenerator<Long> generator;
    private final FileLockContentionHandler fileLockContentionHandler;
    private final long shortTimeoutMs = 10000;
    private final TimeProvider timeProvider = new TrueTimeProvider();

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, FileLockContentionHandler fileLockContentionHandler) {
        this(metaDataProvider, DEFAULT_LOCK_TIMEOUT, fileLockContentionHandler);
    }

    public DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, int lockTimeoutMs, FileLockContentionHandler fileLockContentionHandler) {
        this(metaDataProvider, lockTimeoutMs, fileLockContentionHandler, new RandomLongIdGenerator());
    }

    DefaultFileLockManager(ProcessMetaDataProvider metaDataProvider, int lockTimeoutMs, FileLockContentionHandler fileLockContentionHandler,
                           IdGenerator<Long> generator) {
        this.metaDataProvider = metaDataProvider;
        this.lockTimeoutMs = lockTimeoutMs;
        this.fileLockContentionHandler = fileLockContentionHandler;
        this.generator = generator;
    }

    public FileLock lock(File target, LockOptions options, String targetDisplayName) throws LockTimeoutException {
        return lock(target, options, targetDisplayName, "");
    }

    public FileLock lock(File target, LockOptions options, String targetDisplayName, String operationDisplayName) {
        if (options.getMode() == LockMode.None) {
            throw new UnsupportedOperationException(String.format("No %s mode lock implementation available.", options));
        }
        File canonicalTarget = FileUtils.canonicalize(target);
        if (!lockedFiles.add(canonicalTarget)) {
            throw new IllegalStateException(String.format("Cannot lock %s as it has already been locked by this process.", targetDisplayName));
        }
        try {
            int port = fileLockContentionHandler.reservePort();
            return new DefaultFileLock(canonicalTarget, options, targetDisplayName, operationDisplayName, port);
        } catch (Throwable t) {
            lockedFiles.remove(canonicalTarget);
            throw throwAsUncheckedException(t);
        }
    }

    public void allowContention(FileLock fileLock, Runnable whenContended) {
        DefaultFileLock internalLock = (DefaultFileLock) fileLock;
        fileLockContentionHandler.start(internalLock.lockId, whenContended);
    }

    private class DefaultFileLock extends AbstractFileAccess implements FileLock {
        private final File lockFile;
        private final File target;
        private final LockMode mode;
        private final String displayName;
        private final String operationDisplayName;
        private java.nio.channels.FileLock lock;
        private LockFileAccess lockFileAccess;
        private LockState lockState;
        private int port;
        private final long lockId;

        public DefaultFileLock(File target, LockOptions options, String displayName, String operationDisplayName, int port) throws Throwable {
            this.port = port;
            this.lockId = generator.generateId();
            if (options.getMode() == LockMode.None) {
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
            try {
                lockFile.createNewFile();
            } catch (IOException e) {
                LOGGER.info("Couldn't create lock file for {}", lockFile);
                throw e;
            }

            LockStateSerializer stateProtocol = options.isUseCrossVersionImplementation() ? new Version1LockStateSerializer() : new DefaultLockStateSerializer();
            lockFileAccess = new LockFileAccess(lockFile, new LockStateAccess(stateProtocol));
            try {
                lockState = lock(options.getMode());
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
            return !lockState.isDirty();
        }

        public State getState() {
            assertOpen();
            return lockState;
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
                lockState = lockFileAccess.markDirty(lockState);
                action.run();
                lockState = lockFileAccess.markClean(lockState);
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
            if (lockState.isDirty()) {
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
                                    info = lockInformationRegion(LockMode.Exclusive, Timers.startTimer(shortTimeoutMs));
                                } catch (InterruptedException e) {
                                    throw throwAsUncheckedException(e);
                                }
                                if (info != null) {
                                    try {
                                        lockFileAccess.clearLockInfo();
                                    } finally {
                                        info.release();
                                    }
                                }
                            }
                        } finally {
                            lockFileAccess.close();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to release lock on " + displayName, e);
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

        private LockState lock(FileLockManager.LockMode lockMode) throws Throwable {
            LOGGER.debug("Waiting to acquire {} lock on {}.", lockMode.toString().toLowerCase(), displayName);
            CountdownTimer timer = Timers.startTimer(lockTimeoutMs);

            // Lock the state region, with the requested mode
            java.nio.channels.FileLock stateRegionLock = lockStateRegion(lockMode, timer);
            if (stateRegionLock == null) {
                LockInfo lockInfo = readInformationRegion(Timers.startTimer(shortTimeoutMs));
                throw new LockTimeoutException(displayName, lockInfo.pid, metaDataProvider.getProcessIdentifier(), lockInfo.operation, operationDisplayName, lockFile);
            }

            try {
                LockState lockState;
                if (!stateRegionLock.isShared()) {
                    // We have an exclusive lock (whether we asked for it or not).

                    // Update the state region
                    lockState = lockFileAccess.ensureLockState();

                    // Acquire an exclusive lock on the information region and write our details there
                    java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Exclusive, Timers.startTimer(shortTimeoutMs));
                    if (informationRegionLock == null) {
                        throw new IllegalStateException(String.format("Unable to lock the information region for %s", displayName));
                    }
                    // check that the length of the reserved region is enough for storing our content
                    try {
                        lockFileAccess.writeLockInfo(port, lockId, metaDataProvider.getProcessIdentifier(), operationDisplayName);
                    } finally {
                        informationRegionLock.release();
                    }
                } else {
                    // Just read the state region
                    lockState = lockFileAccess.readLockState();
                }
                LOGGER.debug("Lock acquired.");
                lock = stateRegionLock;
                return lockState;
            } catch (Throwable t) {
                stateRegionLock.release();
                throw t;
            }
        }

        private LockInfo readInformationRegion(CountdownTimer timer) throws IOException, InterruptedException {
            // Can't acquire lock, get details of owner to include in the error message
            LockInfo out = new LockInfo();
            java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Shared, timer);
            if (informationRegionLock == null) {
                LOGGER.debug("Could not lock information region for {}. Ignoring.", displayName);
            } else {
                try {
                    out = lockFileAccess.readLockInfo();
                } finally {
                    informationRegionLock.release();
                }
            }
            return out;
        }

        private java.nio.channels.FileLock lockStateRegion(LockMode lockMode, final CountdownTimer timer) throws IOException, InterruptedException {
            do {
                java.nio.channels.FileLock fileLock = lockFileAccess.tryLockState(lockMode == LockMode.Shared);
                if (fileLock != null) {
                    return fileLock;
                }
                if (port != -1) { //we don't like the assumption about the port very much
                    LockInfo lockInfo = readInformationRegion(timer);
                    if (lockInfo.port != -1) {
                        LOGGER.debug("The file lock is held by a different Gradle process (pid: {}, operation: {}). Will attempt to ping owner at port {}", lockInfo.pid, lockInfo.operation, lockInfo.port);
                        fileLockContentionHandler.pingOwner(lockInfo.port, lockInfo.lockId, displayName);
                    } else {
                        LOGGER.debug("The file lock is held by a different Gradle process. I was unable to read on which port the owner listens for lock access requests.");
                    }
                }
                //TODO SF we should inform on the progress/status bar that we're waiting
                Thread.sleep(200L);
            } while (!timer.hasExpired());
            return null;
        }

        private java.nio.channels.FileLock lockInformationRegion(LockMode lockMode, CountdownTimer timer) throws IOException, InterruptedException {
            do {
                java.nio.channels.FileLock fileLock = lockFileAccess.tryLockInfo(lockMode == LockMode.Shared);
                if (fileLock != null) {
                    return fileLock;
                }
                Thread.sleep(200L);
            }
            while (!timer.hasExpired());
            return null;
        }
    }
}
