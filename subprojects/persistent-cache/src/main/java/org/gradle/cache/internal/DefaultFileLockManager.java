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

import org.gradle.cache.FileIntegrityViolationException;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.InsufficientLockModeException;
import org.gradle.cache.LockOptions;
import org.gradle.cache.LockTimeoutException;
import org.gradle.cache.internal.filelock.DefaultLockStateSerializer;
import org.gradle.cache.internal.filelock.LockFileAccess;
import org.gradle.cache.internal.filelock.LockInfo;
import org.gradle.cache.internal.filelock.LockState;
import org.gradle.cache.internal.filelock.LockStateAccess;
import org.gradle.cache.internal.filelock.LockStateSerializer;
import org.gradle.cache.internal.filelock.Version1LockStateSerializer;
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.RandomLongIdGenerator;
import org.gradle.internal.time.CountdownTimer;
import org.gradle.internal.time.Time;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * Uses file system locks on a lock file per target file.
 */
public class DefaultFileLockManager implements FileLockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileLockManager.class);
    public static final int DEFAULT_LOCK_TIMEOUT = 60000;

    private final Set<File> lockedFiles = new CopyOnWriteArraySet<File>();
    private final ProcessMetaDataProvider metaDataProvider;
    private final int lockTimeoutMs;
    private final IdGenerator<Long> generator;
    private final FileLockContentionHandler fileLockContentionHandler;
    private final int shortTimeoutMs = 10000;

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
        return lock(target, options, targetDisplayName, operationDisplayName, null);
    }

    public FileLock lock(File target, LockOptions options, String targetDisplayName, String operationDisplayName, Runnable whenContended) {
        if (options.getMode() == LockMode.None) {
            throw new UnsupportedOperationException(String.format("No %s mode lock implementation available.", options));
        }
        File canonicalTarget = FileUtils.canonicalize(target);
        if (!lockedFiles.add(canonicalTarget)) {
            throw new IllegalStateException(String.format("Cannot lock %s as it has already been locked by this process.", targetDisplayName));
        }
        try {
            int port = fileLockContentionHandler.reservePort();
            return new DefaultFileLock(canonicalTarget, options, targetDisplayName, operationDisplayName, port, whenContended);
        } catch (Throwable t) {
            lockedFiles.remove(canonicalTarget);
            throw throwAsUncheckedException(t);
        }
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

        public DefaultFileLock(File target, LockOptions options, String displayName, String operationDisplayName, int port, Runnable whenContended) throws Throwable {
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
                if (whenContended != null) {
                    fileLockContentionHandler.start(lockId, whenContended);
                }
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
                                    info = lockInformationRegion(LockMode.Exclusive, new ExponentialBackoff(shortTimeoutMs));
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

        private LockState lock(LockMode lockMode) throws Throwable {
            LOGGER.debug("Waiting to acquire {} lock on {}.", lockMode.toString().toLowerCase(), displayName);

            // Lock the state region, with the requested mode
            java.nio.channels.FileLock stateRegionLock = lockStateRegion(lockMode);
            if (stateRegionLock == null) {
                LockInfo lockInfo = readInformationRegion(new ExponentialBackoff(shortTimeoutMs));
                throw new LockTimeoutException(displayName, lockInfo.pid, metaDataProvider.getProcessIdentifier(), lockInfo.operation, operationDisplayName, lockFile);
            }

            try {
                LockState lockState;
                if (!stateRegionLock.isShared()) {
                    // We have an exclusive lock (whether we asked for it or not).

                    // Update the state region
                    lockState = lockFileAccess.ensureLockState();

                    // Acquire an exclusive lock on the information region and write our details there
                    java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Exclusive, new ExponentialBackoff(shortTimeoutMs));
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
                LOGGER.debug("Lock acquired on {}.", displayName);
                lock = stateRegionLock;
                return lockState;
            } catch (Throwable t) {
                stateRegionLock.release();
                throw t;
            }
        }

        private LockInfo readInformationRegion(ExponentialBackoff backoff) throws IOException, InterruptedException {
            // Can't acquire lock, get details of owner to include in the error message
            LockInfo out = new LockInfo();
            java.nio.channels.FileLock informationRegionLock = lockInformationRegion(LockMode.Shared, backoff);
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

        private java.nio.channels.FileLock lockStateRegion(final LockMode lockMode) throws IOException, InterruptedException {
            final ExponentialBackoff backoff = new ExponentialBackoff(lockTimeoutMs);
            return backoff.retryUntil(new IOQuery<java.nio.channels.FileLock>() {
                private long lastPingTime;
                private int lastLockHolderPort;

                @Override
                public java.nio.channels.FileLock run() throws IOException, InterruptedException {
                    java.nio.channels.FileLock fileLock = lockFileAccess.tryLockState(lockMode == LockMode.Shared);
                    if (fileLock != null) {
                        return fileLock;
                    }
                    if (port != -1) { //we don't like the assumption about the port very much
                        LockInfo lockInfo = readInformationRegion(backoff);
                        if (lockInfo.port != -1) {
                            if (lockInfo.port != lastLockHolderPort) {
                                backoff.restartTimer();
                                lastLockHolderPort = lockInfo.port;
                                lastPingTime = 0;
                            }
                            if (fileLockContentionHandler.maybePingOwner(lockInfo.port, lockInfo.lockId, displayName, backoff.timer.getElapsedMillis() - lastPingTime)) {
                                lastPingTime = backoff.timer.getElapsedMillis();
                                LOGGER.debug("The file lock is held by a different Gradle process (pid: {}, lockId: {}). Pinged owner at port {}", lockInfo.pid, lockInfo.lockId, lockInfo.port);
                            }
                        } else {
                            LOGGER.debug("The file lock is held by a different Gradle process. I was unable to read on which port the owner listens for lock access requests.");
                        }
                    }
                    return null;
                }
            });
        }

        private java.nio.channels.FileLock lockInformationRegion(final LockMode lockMode, ExponentialBackoff backoff) throws IOException, InterruptedException {
            return backoff.retryUntil(new IOQuery<java.nio.channels.FileLock>() {
                @Override
                public java.nio.channels.FileLock run() throws IOException {
                    return lockFileAccess.tryLockInfo(lockMode == LockMode.Shared);
                }
            });
        }
    }

    private interface IOQuery<T> {
        T run() throws IOException, InterruptedException;
    }

    private static class ExponentialBackoff {

        private static final int CAP_FACTOR = 100;

        private static final long SLOT_TIME = 25L;

        private final Random random = new Random();

        private final int timeoutMs;
        private CountdownTimer timer;

        private ExponentialBackoff(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            restartTimer();
        }

        private void restartTimer() {
            timer = Time.startCountdownTimer(timeoutMs);
        }

        <T> T retryUntil(IOQuery<T> query) throws IOException, InterruptedException {
            int iteration = 0;
            T result;
            while ((result = query.run()) == null) {
                if (timer.hasExpired()) {
                    break;
                }
                Thread.sleep(backoffPeriodFor(++iteration));
            }
            return result;
        }

        long backoffPeriodFor(int iteration) {
            return random.nextInt(Math.min(iteration, CAP_FACTOR)) * SLOT_TIME;
        }
    }
}
